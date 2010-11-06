package de.oliver_heger.mediastore.client;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;

import com.google.gwt.user.client.rpc.AsyncCallback;

import de.oliver_heger.mediastore.shared.model.Artist;
import de.oliver_heger.mediastore.shared.search.MediaSearchParameters;
import de.oliver_heger.mediastore.shared.search.MediaSearchServiceAsync;
import de.oliver_heger.mediastore.shared.search.SearchIterator;
import de.oliver_heger.mediastore.shared.search.SearchResult;

/**
 * Test class for {@code ArtistQueryHandler}. This class also tests
 * functionality of the base class.
 *
 * @author Oliver Heger
 * @version $Id: $
 */
public class TestArtistQueryHandler
{
    /** Constant for the name of a test artist. */
    private static final String ARTIST_NAME = "TestArtist_";

    /** Constant for a client parameter object. */
    private static final Serializable CLIENT_PARAM = Integer.valueOf(111);

    /** Constant for the limit of search results. */
    private static final int LIMIT = 10;

    /** A mock for the view component. */
    private SearchResultView view;

    /** A mock for the search service. */
    private MediaSearchServiceAsync searchService;

    /** The handler to be tested. */
    private ArtistQueryHandlerTestImpl handler;

    @Before
    public void setUp() throws Exception
    {
        view = EasyMock.createMock(SearchResultView.class);
        searchService = EasyMock.createMock(MediaSearchServiceAsync.class);
        handler = new ArtistQueryHandlerTestImpl(view, searchService);
    }

    /**
     * Creates a mock object for a search result.
     *
     * @return the mock
     */
    private static SearchResult<Artist> createResultMock()
    {
        @SuppressWarnings("unchecked")
        SearchResult<Artist> result = EasyMock.createMock(SearchResult.class);
        return result;
    }

    /**
     * Creates a search parameters object with default data.
     *
     * @return the parameters object
     */
    private static MediaSearchParameters createSearchParams()
    {
        MediaSearchParameters params = new MediaSearchParameters();
        params.setSearchText("A search text");
        params.setMaxResults(42);
        return params;
    }

    /**
     * Creates a list with test artists. This list can be returned from a mock
     * search result object.
     *
     * @param artistCount the number of artists to add to the list
     * @param artistName
     * @return
     */
    private List<Artist> createArtistList(int artistCount)
    {
        List<Artist> artists = new ArrayList<Artist>(artistCount);
        for (int i = 0; i < artistCount; i++)
        {
            Artist a = new Artist();
            a.setName(ARTIST_NAME + i);
            artists.add(a);
        }
        return artists;
    }

    /**
     * Tests whether the correct view is returned.
     */
    @Test
    public void testGetView()
    {
        assertSame("Wrong view", view, handler.getView());
    }

    /**
     * Tests whether a correct result data object is created.
     */
    @Test
    public void testCreateResultData()
    {
        SearchResult<Artist> res = createResultMock();
        final int artistCount = 16;
        List<Artist> artists = createArtistList(artistCount);
        EasyMock.expect(res.getResults()).andReturn(artists);
        EasyMock.replay(res);
        ResultData data = handler.createResult(res);
        assertEquals("Wrong number of columns", 1, data.getColumnCount());
        assertEquals("Wrong column name (1)", "Name", data.getColumnName(0));
        assertEquals("Wrong row count", artistCount, data.getRowCount());
        for (int i = 0; i < artistCount; i++)
        {
            assertEquals("Wrong name at " + i, ARTIST_NAME + i,
                    data.getValueAt(i, 0));
        }
    }

    /**
     * Tests whether a search query is correctly initiated.
     */
    @Test
    public void testHandleQuery()
    {
        SearchIterator sit = EasyMock.createMock(SearchIterator.class);
        AsyncCallback<SearchResult<Artist>> callback =
                handler.initCallbackMock();
        MediaSearchParameters params = createSearchParams();
        searchService.searchArtists(params, sit, callback);
        EasyMock.replay(sit, callback, searchService);
        handler.handleQuery(params, sit);
        EasyMock.verify(sit, callback, searchService);
    }

    /**
     * Helper method for checking the onSuccess() implementation of the query
     * callback for paged results.
     *
     * @param currentPage the index of the current page
     * @param pageCount the total number of pages
     * @param morePages the expected flag whether more results are available
     */
    private void checkQueryCallbackOnSuccessPaged(Integer currentPage,
            Integer pageCount, boolean morePages)
    {
        SearchResult<Artist> result = createResultMock();
        SearchIterator sit = EasyMock.createMock(SearchIterator.class);
        ResultData data = handler.initResultDataMock(result);
        MediaSearchParameters params = new MediaSearchParameters();
        params.setClientParameter(CLIENT_PARAM);
        EasyMock.expect(result.getSearchParameters()).andReturn(params);
        EasyMock.expect(result.getSearchIterator()).andReturn(sit);
        EasyMock.expect(sit.hasNext()).andReturn(Boolean.FALSE);
        EasyMock.expect(sit.getCurrentPage()).andReturn(currentPage);
        EasyMock.expect(sit.getPageCount()).andReturn(pageCount);
        view.addSearchResults(data, CLIENT_PARAM);
        view.searchComplete(sit, CLIENT_PARAM, morePages);
        EasyMock.replay(result, sit, data, view, searchService);
        AsyncCallback<SearchResult<Artist>> callback =
                handler.createQueryCallback(params);
        callback.onSuccess(result);
        EasyMock.verify(result, sit, data, view, searchService);
    }

    /**
     * Tests the onSuccess() method of the query callback for a paged result if
     * the last page is reached.
     */
    @Test
    public void testQueryCallbackOnSuccessPagedNoMorePages()
    {
        checkQueryCallbackOnSuccessPaged(1, 2, false);
    }

    /**
     * Tests the onSuccess() method of the query callback for a paged result if
     * more pages are available.
     */
    @Test
    public void testQueryCallbackOnSuccessPagedMorePages()
    {
        checkQueryCallbackOnSuccessPaged(0, 2, true);
    }

    /**
     * Tests the onSuccess() method of the query callback for a chunk search if
     * no more data is available.
     */
    @Test
    public void testQueryCallbackOnSuccessChunkNoMoreData()
    {
        SearchResult<Artist> result = createResultMock();
        SearchIterator sit = EasyMock.createMock(SearchIterator.class);
        ResultData data = handler.initResultDataMock(result);
        MediaSearchParameters params = new MediaSearchParameters();
        params.setClientParameter(CLIENT_PARAM);
        EasyMock.expect(result.getSearchParameters()).andReturn(params);
        EasyMock.expect(result.getSearchIterator()).andReturn(sit);
        EasyMock.expect(sit.hasNext()).andReturn(Boolean.FALSE);
        EasyMock.expect(sit.getCurrentPage()).andReturn(null);
        view.addSearchResults(data, CLIENT_PARAM);
        view.searchComplete(sit, CLIENT_PARAM, false);
        EasyMock.replay(result, sit, data, view, searchService);
        AsyncCallback<SearchResult<Artist>> callback =
                handler.createQueryCallback(params);
        callback.onSuccess(result);
        EasyMock.verify(result, sit, data, view, searchService);
    }

    /**
     * Helper method for testing onSuccess() of the query callback for a chunk
     * search if more data is available and the limit of search results is not
     * yet reached. This method can be used to test a search with a specific
     * limit or an unlimited search.
     *
     * @param maxResults the original maxResults parameter
     * @param nextMaxResults the maxResults parameter for the next invocation
     * @param artistCount the number of found artists
     */
    private void checkQueryCallbackOnSuccessChunkMoreData(int maxResults,
            int nextMaxResults, int artistCount)
    {
        SearchResult<Artist> result = createResultMock();
        SearchIterator sit = EasyMock.createMock(SearchIterator.class);
        ResultData data = handler.initResultDataMock(result);
        MediaSearchParameters params = new MediaSearchParameters();
        params.setClientParameter(CLIENT_PARAM);
        params.setMaxResults(maxResults);
        MediaSearchParameters params2 = new MediaSearchParameters();
        params2.setClientParameter(CLIENT_PARAM);
        params2.setMaxResults(nextMaxResults);
        List<Artist> artists = createArtistList(artistCount);
        EasyMock.expect(result.getSearchParameters()).andReturn(params);
        EasyMock.expect(result.getSearchIterator()).andReturn(sit);
        EasyMock.expect(result.getResults()).andReturn(artists);
        EasyMock.expect(sit.hasNext()).andReturn(Boolean.TRUE);
        view.addSearchResults(data, CLIENT_PARAM);
        AsyncCallback<SearchResult<Artist>> callback =
                handler.createQueryCallback(params);
        searchService.searchArtists(params2, sit, callback);
        EasyMock.replay(result, sit, data, view, searchService);
        callback.onSuccess(result);
        EasyMock.verify(result, sit, data, view, searchService);
    }

    /**
     * Tests the onSuccess() method of the query callback for a chunk search if
     * more data is available and the limit of search results is not yet
     * reached.
     */
    @Test
    public void testQueryCallbackOnSuccessChunkMoreData()
    {
        final int artistCount = 3;
        checkQueryCallbackOnSuccessChunkMoreData(LIMIT, LIMIT - artistCount,
                artistCount);
    }

    /**
     * Tests the onSuccess() method of the query callback for a chunk search if
     * more data is available and there is not limit of search results.
     */
    @Test
    public void testQueryCallbackOnSuccessChunkMoreDataUnlimited()
    {
        checkQueryCallbackOnSuccessChunkMoreData(-1, 0, LIMIT);
    }

    /**
     * Tests the onSuccess() method of the query callback for a chunk search if
     * more data is available and the limit of search results has been reached.
     */
    @Test
    public void testQueryCallbackOnSuccessChunkMoreDataLimitReached()
    {
        SearchResult<Artist> result = createResultMock();
        SearchIterator sit = EasyMock.createMock(SearchIterator.class);
        ResultData data = handler.initResultDataMock(result);
        final String searchText = "TestSearchText";
        MediaSearchParameters params = new MediaSearchParameters();
        params.setClientParameter(CLIENT_PARAM);
        params.setMaxResults(LIMIT);
        params.setSearchText(searchText);
        MediaSearchParameters params2 = new MediaSearchParameters();
        params2.setMaxResults(1);
        params2.setClientParameter(CLIENT_PARAM);
        params2.setSearchText(searchText);
        List<Artist> artists = createArtistList(LIMIT);
        AsyncCallback<SearchResult<Artist>> callback =
                handler.createQueryCallback(params);
        AsyncCallback<SearchResult<Artist>> moreResultsCb =
                handler.initCallbackMock();
        EasyMock.expect(result.getSearchParameters()).andReturn(params)
                .anyTimes();
        EasyMock.expect(result.getSearchIterator()).andReturn(sit).anyTimes();
        EasyMock.expect(result.getResults()).andReturn(artists);
        EasyMock.expect(sit.hasNext()).andReturn(Boolean.TRUE);
        view.addSearchResults(data, CLIENT_PARAM);
        searchService.searchArtists(params2, sit, moreResultsCb);
        EasyMock.replay(result, sit, data, moreResultsCb, view, searchService);
        callback.onSuccess(result);
        EasyMock.verify(result, sit, data, moreResultsCb, view, searchService);
    }

    /**
     * Creates a callback instance for searching for more results.
     *
     * @param params the search parameters object
     * @param result the search result object
     * @return the callback
     */
    private AsyncCallback<SearchResult<Artist>> createMoreResultsCb(
            MediaSearchParameters params, SearchResult<Artist> result)
    {
        AbstractOverviewQueryHandler<Artist>.QueryCallback cb =
                (AbstractOverviewQueryHandler<Artist>.QueryCallback) handler
                        .createQueryCallback(params);
        return handler.createMoreResultsCallback(cb, result);
    }

    /**
     * Tests the onSuccess() method of the more results callback if no more data
     * is available.
     */
    @Test
    public void testMoreResultsCallbackOnSuccessNoMoreData()
    {
        SearchResult<Artist> result = createResultMock();
        SearchResult<Artist> result2 = createResultMock();
        SearchIterator sit = EasyMock.createMock(SearchIterator.class);
        SearchIterator sit2 = EasyMock.createMock(SearchIterator.class);
        MediaSearchParameters params = new MediaSearchParameters();
        params.setClientParameter(CLIENT_PARAM);
        EasyMock.expect(result.getSearchIterator()).andReturn(sit);
        EasyMock.expect(result.getSearchParameters()).andReturn(params);
        EasyMock.expect(result2.getResults())
                .andReturn(new ArrayList<Artist>());
        EasyMock.expect(result2.getSearchIterator()).andReturn(sit2);
        EasyMock.expect(sit2.hasNext()).andReturn(Boolean.FALSE);
        view.searchComplete(sit, CLIENT_PARAM, false);
        EasyMock.replay(result, result2, sit, sit2, view, searchService);
        createMoreResultsCb(params, result).onSuccess(result2);
        EasyMock.verify(result, result2, sit, sit2, view, searchService);
    }

    /**
     * Tests the onSuccess() method of the more results callback if more data is
     * available.
     */
    @Test
    public void testMoreResultsCallbackOnSuccessMoreData()
    {
        SearchResult<Artist> result = createResultMock();
        SearchResult<Artist> result2 = createResultMock();
        SearchIterator sit = EasyMock.createMock(SearchIterator.class);
        MediaSearchParameters params = new MediaSearchParameters();
        params.setClientParameter(CLIENT_PARAM);
        EasyMock.expect(result2.getResults())
                .andReturn(new ArrayList<Artist>());
        EasyMock.expect(result2.getSearchIterator()).andReturn(sit);
        EasyMock.expect(result2.getSearchParameters()).andReturn(params);
        EasyMock.expect(sit.hasNext()).andReturn(Boolean.TRUE);
        AsyncCallback<SearchResult<Artist>> callback =
                createMoreResultsCb(params, result);
        searchService.searchArtists(params, sit, callback);
        EasyMock.replay(result, result2, sit, view, searchService);
        callback.onSuccess(result2);
        EasyMock.verify(result, result2, sit, view, searchService);
    }

    /**
     * Tests the onSuccess() method of the more results callback if a result is
     * found.
     */
    @Test
    public void testMoreResultsCallbackOnSuccessGotResult()
    {
        SearchResult<Artist> result = createResultMock();
        SearchResult<Artist> result2 = createResultMock();
        SearchIterator sit = EasyMock.createMock(SearchIterator.class);
        MediaSearchParameters params = new MediaSearchParameters();
        params.setClientParameter(CLIENT_PARAM);
        EasyMock.expect(result.getSearchIterator()).andReturn(sit);
        EasyMock.expect(result.getSearchParameters()).andReturn(params);
        EasyMock.expect(result2.getResults()).andReturn(createArtistList(1));
        view.searchComplete(sit, CLIENT_PARAM, true);
        EasyMock.replay(result, result2, sit, view, searchService);
        createMoreResultsCb(params, result).onSuccess(result2);
        EasyMock.verify(result, result2, sit, view, searchService);
    }

    /**
     * Tests the onFailure() method of the query callback.
     */
    @Test
    public void testQueryCallbackOnFailure()
    {
        Throwable err = new RuntimeException();
        view.onFailure(err, CLIENT_PARAM);
        EasyMock.replay(view, searchService);
        MediaSearchParameters params = new MediaSearchParameters();
        params.setClientParameter(CLIENT_PARAM);
        AsyncCallback<SearchResult<Artist>> callback =
                handler.createQueryCallback(params);
        callback.onFailure(err);
        EasyMock.verify(view, searchService);
    }

    /**
     * Tests the onFailure() method of the more results callback.
     */
    @Test
    public void testMoreResultsCallbackInFailure()
    {
        SearchResult<Artist> result = createResultMock();
        Throwable err = new RuntimeException();
        view.onFailure(err, CLIENT_PARAM);
        EasyMock.replay(result, view, searchService);
        MediaSearchParameters params = new MediaSearchParameters();
        params.setClientParameter(CLIENT_PARAM);
        AsyncCallback<SearchResult<Artist>> callback =
                createMoreResultsCb(params, result);
        callback.onFailure(err);
        EasyMock.verify(result, view, searchService);
    }

    /**
     * A test implementation of the query handler which allows mocking the
     * search service and other functionality.
     */
    private static class ArtistQueryHandlerTestImpl extends ArtistQueryHandler
    {
        /** The mock search service. */
        private final MediaSearchServiceAsync mockSearchService;

        /** A mock callback object. */
        private AsyncCallback<SearchResult<Artist>> mockCallback;

        /** A mock ResultData object. */
        private ResultData mockResultData;

        /** The expected SearchResult object. */
        private SearchResult<Artist> expectedSearchResult;

        public ArtistQueryHandlerTestImpl(SearchResultView view,
                MediaSearchServiceAsync svc)
        {
            super(view);
            mockSearchService = svc;
        }

        /**
         * Creates and installs a mock callback object.
         *
         * @return the mock object
         */
        @SuppressWarnings("unchecked")
        public AsyncCallback<SearchResult<Artist>> initCallbackMock()
        {
            mockCallback = EasyMock.createMock(AsyncCallback.class);
            return mockCallback;
        }

        /**
         * Initializes a mock ResultData object.
         *
         * @param expSR the expected search result object
         * @return the mock result data object
         */
        public ResultData initResultDataMock(SearchResult<Artist> expSR)
        {
            mockResultData = EasyMock.createMock(ResultData.class);
            expectedSearchResult = expSR;
            return mockResultData;
        }

        /**
         * Either returns the mock search service or calls the super method.
         */
        @Override
        public MediaSearchServiceAsync getSearchService()
        {
            return (mockSearchService != null) ? mockSearchService : super
                    .getSearchService();
        }

        /**
         * Either returns the mock result data object or calls the super method.
         */
        @Override
        protected ResultData createResult(SearchResult<Artist> result)
        {
            if (mockResultData != null)
            {
                assertSame("Wrong search result", expectedSearchResult, result);
                return mockResultData;
            }
            return super.createResult(result);
        }

        /**
         * Either returns the mock callback or calls the super method.
         */
        @Override
        protected AsyncCallback<SearchResult<Artist>> createQueryCallback(
                MediaSearchParameters params)
        {
            return (mockCallback != null) ? mockCallback : super
                    .createQueryCallback(params);
        }

        /**
         * Either returns the mock callback or calls the super method.
         */
        @Override
        AsyncCallback<SearchResult<Artist>> createMoreResultsCallback(
                QueryCallback parent, SearchResult<Artist> result)
        {
            return (mockCallback != null) ? mockCallback : super
                    .createMoreResultsCallback(parent, result);
        }
    }
}
