package de.oliver_heger.splaya.engine;

import scala.actors.Actor
import scala.collection.mutable.Queue
import org.slf4j.LoggerFactory
import java.util.Arrays
import javax.sound.sampled.SourceDataLine
import java.io.IOException

/**
 * An actor for handling audio playback.
 *
 * This actor receives messages when new audio data has been streamed to the
 * temporary buffer. Its task is to process the single audio streams and send
 * them chunk-wise to the ''line write actor''. It also has to manage a data
 * line for actually playing audio data.
 *
 * This actor and the [[de.oliver_heger.splaya.engine.LineWriteActor]] actor
 * play a kind of ping pong when processing audio streams: ''PlaybackActor''
 * sends a chunk of data to be played, ''LineWriteActor'' feeds it into the
 * line and sends a message back when this is complete. This causes
 * ''PlaybackActor'' to send the next block of data. If no more data is left,
 * playback stops until the temporary buffer on the hard disk is filled again.
 *
 * ''PlaybackActor'' also reacts on messages for pausing and resuming playback.
 * If a line is open, it is directly stopped and started, respectively.
 *
 * @param ctxFactory the factory for creating playback context objects
 * @param streamFactory a factory for creating stream objects
 * @param initialSkip the skip position for the first file to be played
 */
class PlaybackActor(ctxFactory: PlaybackContextFactory,
  streamFactory: SourceStreamWrapperFactory, initialSkip: Long) extends Actor {
  /**
   * Constant for the default buffer size. This size is used if no playback
   * buffer is available.
   */
  private val DefaultBufferSize = 4096

  /** The logger. */
  private val log = LoggerFactory.getLogger(classOf[Exit])

  /** A queue with the audio sources to be played.*/
  private val queue = Queue.empty[AudioSource]

  /** The current context. */
  private var context: PlaybackContext = _

  /** The current input stream. */
  private var stream: SourceStreamWrapper = _

  /** The buffer for feeding the line. */
  private var playbackBuffer: Array[Byte] = _

  /** Holds the currently played audio source. */
  private var currentSource: AudioSource = _

  /** The current position in the audio stream. */
  private var streamPosition = 0L

  /** The current skip position. */
  private var skipPosition = initialSkip

  /** A flag whether playback is enabled. */
  private var playbackEnabled = true

  /** A flag whether currently a chunk is played. */
  private var chunkPlaying = false

  /** A flag whether the end of the playlist is reached. */
  private var endOfPlaylist = false

  /**
   * A flag whether a read error occurred. If this flag is set, data is read
   * from the original data stream rather than from the audio stream because
   * the stream is just skipped.
   */
  private var errorStream = false

  /**
   * The main message loop of this actor.
   */
  def act() {
    var running = true

    while (running) {
      receive {
        case ex: Exit =>
          running = false
          if (context != null) {
            context.close()
          }
          ex.confirmed(this)

        case src: AudioSource =>
          enqueue(src)

        case temp: TempFile =>
          enqueueTempFile(temp)

        case ChunkPlayed =>
          chunkPlaying = false
          playback()

        case StopPlayback =>
          playbackEnabled = false
          updateLine(_.stop())

        case StartPlayback =>
          playbackEnabled = true
          updateLine(_.start())
          playback()

        case SkipCurrentSource =>
          skipCurrentSource()

        case PlaylistEnd =>
          endOfPlaylist = true
          playback()
      }
    }
  }

  /**
   * Returns a string for this object. This string contains the name of this
   * actor.
   * @return a string for this object
   */
  override def toString = "PlaybackActor"

  /**
   * Adds the specified audio source to the internal queue of songs to play.
   * If no song is currently played, playback starts.
   * @param src the audio source
   */
  private def enqueue(src: AudioSource) {
    queue += src
    if (context == null) {
      playback()
    }
  }

  /**
   * Adds the specified temporary file to the buffer. Then checks whether
   * playback can be started.
   * @param temp the temporary file to be added
   */
  private def enqueueTempFile(temp: TempFile) {
    streamFactory.bufferManager += temp
    playback()
  }

  /**
   * Initiates playback.
   */
  private def playback() {
    if (isPlaybackAllowed) {
      playChunk()
    }
  }

  /**
   * Checks whether playback can be initiated.
   */
  private def isPlaybackAllowed: Boolean =
    playbackEnabled && !chunkPlaying && sufficientBufferSize && contextAvailable

  /**
   * Determines the size of the playback buffer. If a buffer is already allocated,
   * it can be used directly. Otherwise, a default size is assumed.
   */
  private def playbackBufferSize: Int =
    if (playbackBuffer != null) playbackBuffer.length else DefaultBufferSize

  /**
   * Checks whether the temporary buffer contains enough data to read another
   * chunk of data. In order to prevent incomplete reads, playback of a chunk
   * is started only if the buffer contains a certain amount of data.
   */
  private def sufficientBufferSize: Boolean =
    endOfPlaylist || streamFactory.bufferManager.bufferSize >= 2 * playbackBufferSize

  /**
   * Checks whether a playback context is available. If not, it is tried to
   * create one. If this method returns '''true''', a context is available and
   * can be used for playback.
   */
  private def contextAvailable: Boolean =
    context != null || setUpPlaybackContext()

  /**
   * Plays a chunk of the audio data.
   */
  private def playChunk() {
    val len = readStream()
    if (len < 0) {
      closeCurrentAudioSource()
      playback()
    } else {
      passChunkToLineActor(len)
      streamPosition += len
      chunkPlaying = true
    }
  }

  /**
   * Reads data from the current input stream. If the errorStream flag is set,
   * data is read from the original stream, not from the audio stream. The data
   * read is stored in the playback buffer.
   * @return the number of bytes that were read
   */
  private def readStream(): Int = {
    val is = if (errorStream) stream else context.stream
    var read: Int = -1
    try {
      read = is.read(playbackBuffer)
    } catch {
      case ioex: IOException =>
        val msg = "Error when reading from audio stream for source " + currentSource
        log.error(msg, ioex)
        Gateway.publish(
          PlaybackError(msg, ioex, errorStream))
        if (errorStream) {
          playbackEnabled = false
        } else {
          errorStream = true
          skipCurrentSource()
          read = readStream()
        }
    }
    read
  }

  /**
   * Notifies the line actor to play the current chunk.
   * @param len the size of the current chunk
   */
  private def passChunkToLineActor(len: Int) {
    val line = if (errorStream) null else context.line
    Gateway ! Gateway.ActorLineWrite -> PlayChunk(line,
      Arrays.copyOf(playbackBuffer, playbackBuffer.length), len,
      streamPosition, skipPosition)
  }

  /**
   * Creates the objects required for the playback of a new audio file. If there
   * are no more audio files to play, this method returns <b>false</b>.
   * @return a flag if there are more files to play
   */
  private def setUpPlaybackContext(): Boolean = {
    if (queue.isEmpty) {
      false
    } else {
      preparePlayback()
      true
    }
  }

  /**
   * Prepares the playback of a new audio file and creates the necessary context
   * objects.
   */
  private def preparePlayback() {
    val source = queue.dequeue()
    log.info("Starting playback of {}.", source.uri)

    try {
      val sourceStream = if (stream != null) stream.currentStream else null
      stream = streamFactory.createStream(sourceStream, source.length)
      context = ctxFactory.createPlaybackContext(stream)
      playbackBuffer = context.createPlaybackBuffer()
      prepareLine()
      Gateway.publish(PlaybackSourceStart(source))
      currentSource = source
    } catch {
      case ex: Exception =>
        handleContextCreationError(source, ex)
    }

    streamPosition = 0
  }

  /**
   * Prepares the current line to start playback.
   */
  private def prepareLine() {
    context.line.open(context.format)
    context.line.start()
  }

  /**
   * Handles an exception when creating the playback context for a source.
   * @param source the current audio source
   * @param ex the exception
   */
  private def handleContextCreationError(source: AudioSource, ex: Throwable) {
    val msg = "Cannot create PlaybackContext for source " + source
    log.error(msg, ex)
    Gateway.publish(PlaybackError(msg, ex, false))
    errorStream = true
    skipPosition = Long.MaxValue
    playbackBuffer = new Array[Byte](DefaultBufferSize)
  }

  /**
   * Skips the current audio source.
   */
  private def skipCurrentSource() {
    updateLine { line =>
      line.stop()
      line.flush()
    }
    skipPosition = Long.MaxValue
  }

  /**
   * Closes all resources related to the current audio source.
   */
  private def closeCurrentAudioSource() {
    if (currentSource != null) {
      Gateway.publish(PlaybackSourceEnd(currentSource))
      currentSource = null
    }
    if (context != null) {
      context.close()
      context = null
    }
    playbackBuffer = null
    skipPosition = 0
    errorStream = false
  }

  /**
   * Performs an operation on the current line. If a line is currently open,
   * the function is invoked. Otherwise, this method has no effect.
   * @param f the function to be performed on the line
   */
  private def updateLine(f: SourceDataLine => Unit) {
    if (context != null) {
      f(context.line)
    }
  }
}
