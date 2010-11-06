package de.oliver_heger.mediastore.server.search;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.Query;

import com.google.appengine.api.users.User;
import com.google.appengine.api.users.UserService;
import com.google.appengine.api.users.UserServiceFactory;
import com.google.gwt.user.server.rpc.RemoteServiceServlet;

import de.oliver_heger.mediastore.server.db.JPATemplate;
import de.oliver_heger.mediastore.shared.model.Artist;
import de.oliver_heger.mediastore.shared.search.MediaSearchParameters;
import de.oliver_heger.mediastore.shared.search.MediaSearchService;
import de.oliver_heger.mediastore.shared.search.SearchIterator;
import de.oliver_heger.mediastore.shared.search.SearchIteratorImpl;
import de.oliver_heger.mediastore.shared.search.SearchResult;
import de.oliver_heger.mediastore.shared.search.SearchResultImpl;

/**
 * <p>
 * The Google RPC-based implementation of the {@link MediaSearchService}
 * interface.
 * </p>
 * <p>
 * This implementation supports two different search modes:
 * <ul>
 * <li>If no search text is provided, results are retrieved directly from the
 * database with full paging support. The {@code firstResult} and
 * {@code maxResults} properties of the {@link MediaSearchParameters} object are
 * evaluated. A search iterator object is ignored if one is specified. The
 * iterator object associated with the search result always indicates that no
 * more results are available.</li>
 * <li>If a search text is provided, a chunk-based search is performed because a
 * full table scan has to be performed. In this case only the {@code maxResults}
 * property of the {@link MediaSearchParameters} object is taken into account.
 * The first position of the result set is determined by the passed in search
 * iterator. The client is responsible for invoking the search method repeatedly
 * until all desired results are retrieved or the table has been scanned
 * completely.</li>
 * </ul>
 * Both search modes are implemented by a single method for the corresponding
 * result type.
 * </p>
 *
 * @author Oliver Heger
 * @version $Id: $
 */
public class MediaSearchServiceImpl extends RemoteServiceServlet implements
        MediaSearchService
{
    /**
     * The serial version UID.
     */
    private static final long serialVersionUID = 20100911L;

    /** Constant for the user ID parameter. */
    private static final String PARAM_USRID = "userID";

    /** Constant for the search text parameter. */
    private static final String PARAM_SEARCHTEXT = "searchText";

    /** Constant for the select clause which selects an entity. */
    private static final String SELECT_ENTITY = "select e";

    /** Constant for the select clause for determining the number of results. */
    private static final String SELECT_COUNT = "select count(e)";

    /** Constant for the query select prefix. */
    private static final String SELECT_PREFIX = SELECT_ENTITY + " from ";

    /** Constant for the WHERE part of the query with the user ID constraint. */
    private static final String WHERE_USER = " e where e.userID = :"
            + PARAM_USRID;

    /** Constant for the ORDER BY clause. */
    private static final String ORDER_BY = " order by e.";

    /** Constant for the prefix of the query for artists. */
    private static final String QUERY_ARTISTS_PREFIX = SELECT_PREFIX + "Artist"
            + WHERE_USER;

    /** Constant for the order part of an artist query. */
    private static final String ARTIST_ORDER = ORDER_BY + "name";

    /** Constant for the query for artists without a search text constraint. */
    private static final String QUERY_ARTISTS_ALL = QUERY_ARTISTS_PREFIX
            + ARTIST_ORDER;

    /** Constant for the query for artists with a search condition. */
    private static final String QUERY_ARTISTS_SEARCH = QUERY_ARTISTS_PREFIX
            + " and e.name > :" + PARAM_SEARCHTEXT + ARTIST_ORDER;

    /** Constant for the default chunk size for search operations. */
    private static final int DEFAULT_CHUNK_SIZE = 50;

    /** Stores the chunk size used for search operations. */
    private int chunkSize = DEFAULT_CHUNK_SIZE;

    /**
     * Returns the current chunk size for search operations.
     *
     * @return the chunk size for search operations
     */
    public int getChunkSize()
    {
        return chunkSize;
    }

    /**
     * Sets the chunk size for search operations. This is the number of records
     * processed in a single search step; then the results obtained so far are
     * sent back to the client. The client can then initiate the next search
     * step.
     *
     * @param chunkSize the chunk size
     */
    public void setChunkSize(int chunkSize)
    {
        this.chunkSize = chunkSize;
    }

    /**
     * Performs a search for artists. The search can be performed in multiple
     * iterations. If a search iterator is provided, a search continues.
     *
     * @param params search parameters
     * @param iterator the search iterator
     */
    @Override
    public SearchResult<Artist> searchArtists(MediaSearchParameters params,
            SearchIterator iterator)
    {
        if (params.getSearchText() == null)
        {
            SearchIteratorImpl sit = new SearchIteratorImpl();
            List<Artist> artists =
                    executeFullSearch(params, QUERY_ARTISTS_ALL, sit);
            return new SearchResultImpl<Artist>(artists, sit, params);
        }
        return executeChunkSearch(params, iterator,
                createArtistSearchFilter(params), QUERY_ARTISTS_SEARCH);
    }

    /**
     * Creates some test data for the currently logged in user. This
     * implementation delegates to a helper class for creating the dummy data.
     */
    @Override
    public void createTestData()
    {
        new DummyDataCreator(getCurrentUserID()).createTestData();
    }

    /**
     * Tests the passed in search iterator to determine whether a new search has
     * to be started. This is the case if the iterator is <b>null</b> or new
     * search key is defined. This method also checks whether the iterator is of
     * the expected type. If not, an exception is thrown.
     *
     * @param it the iterator
     * @return a flag whether a new search has to be started
     * @throws IllegalArgumentException if the iterator object is not of the
     *         expected type
     */
    boolean isNewSearch(SearchIterator it)
    {
        if (it == null)
        {
            return true;
        }

        if (!(it instanceof SearchIteratorImpl))
        {
            throw new IllegalArgumentException("Unsupported search iterator: "
                    + it);
        }

        return ((SearchIteratorImpl) it).getSearchKey() == null;
    }

    /**
     * Initializes the search iterator for the current search. This method
     * checks whether a new search is started or a search is continued. Then
     * either a new {@link SearchIteratorImpl} object is created or the existing
     * one is updated correspondingly.
     *
     * @param em the {@code EntityManager}
     * @param query the query string
     * @param it the iterator object passed to the server; this method expects
     *        that it is of type {@code SearchIteratorImpl}, otherwise an
     *        exception is thrown
     * @return the iterator object for the current search
     * @throws IllegalArgumentException if the iterator object is not of the
     *         expected type
     */
    SearchIteratorImpl initializeSearchIterator(EntityManager em, String query,
            SearchIterator it)
    {
        if (isNewSearch(it))
        {
            SearchIteratorImpl sit = newSearchIterator(em, query);
            return sit;
        }
        else
        {
            SearchIteratorImpl sit = (SearchIteratorImpl) it;
            sit.setCurrentPosition(sit.getCurrentPosition() + getChunkSize());
            return sit;
        }
    }

    /**
     * Creates a filter object for an artist search.
     *
     * @param params the search parameters
     * @return the filter for artists
     */
    ArtistSearchFilter createArtistSearchFilter(MediaSearchParameters params)
    {
        assert params.getSearchText() != null : "No search text!";
        return new ArtistSearchFilter(params.getSearchText());
    }

    /**
     * Returns the ID of the currently logged in user. This Id is needed as
     * parameter for many queries.
     *
     * @return the ID of the current user
     * @throws IllegalStateException if no user is logged in
     */
    private String getCurrentUserID()
    {
        UserService userService = UserServiceFactory.getUserService();
        User user = userService.getCurrentUser();
        if (user == null)
        {
            throw new IllegalStateException("No user is logged in!");
        }

        return fetchUserID(user);
    }

    /**
     * Helper method for obtaining the ID of the specified. If no ID is
     * available, the user's mail address is returned. This is mainly needed for
     * testing purposes where no user ID seems to be available.
     *
     * @param user the user
     * @return the ID of this user to use
     */
    private String fetchUserID(User user)
    {
        String uid = user.getUserId();
        if (uid == null)
        {
            uid = user.getEmail();
        }
        return uid;
    }

    /**
     * Helper method for creating a query which has the current user as
     * parameter. This method creates a query for the specified query string and
     * sets the parameter for the user ID.
     *
     * @param em the entity manager
     * @param queryStr the query string
     * @return the query object
     */
    private Query prepareUserQuery(EntityManager em, String queryStr)
    {
        return em.createQuery(queryStr).setParameter(PARAM_USRID,
                getCurrentUserID());
    }

    /**
     * Prepares a search query. The query is initialized with the parameter for
     * the current user and the search parameter.
     *
     * @param em the entity manager
     * @param queryStr the query string
     * @param searchParam the search parameter
     * @return the query object
     */
    private Query prepareSearchQuery(EntityManager em, String queryStr,
            Object searchParam)
    {
        Query query = prepareUserQuery(em, queryStr);
        query.setParameter(PARAM_SEARCHTEXT, searchParam);
        return query;
    }

    /**
     * Initializes a search iterator for a new search. The total record count is
     * already determined.
     *
     * @param em the entity manager
     * @param query the current query string
     * @return the new search iterator
     */
    private SearchIteratorImpl newSearchIterator(EntityManager em, String query)
    {
        SearchIteratorImpl sit = new SearchIteratorImpl();
        String countQuery = createCountQuery(query);
        Number count =
                (Number) prepareSearchQuery(em, countQuery, sit.getSearchKey())
                        .getSingleResult();
        sit.setRecordCount(count.longValue());
        return sit;
    }

    /**
     * Executes a search query over all elements of a given type. This method
     * actually executes two queries: one for determining the total record count
     * and one for retrieving the result objects. The latter are returned as a
     * list. The passed in search iterator is initialized correspondingly.
     *
     * @param params the parameters object with search parameters
     * @param queryStr the query string to be executed
     * @param sit the search iterator to be initialized
     * @return the list with the results
     */
    private <D> List<D> executeFullSearch(final MediaSearchParameters params,
            final String queryStr, final SearchIteratorImpl sit)
    {
        JPATemplate<List<D>> template = new JPATemplate<List<D>>()
        {
            @Override
            protected List<D> performOperation(EntityManager em)
            {
                Number count =
                        (Number) prepareUserQuery(em,
                                createCountQuery(queryStr)).getSingleResult();
                sit.setCurrentPosition(params.getFirstResult());
                sit.setRecordCount(count.longValue());
                sit.setHasNext(false);

                Query query = prepareUserQuery(em, queryStr);
                if (params.getFirstResult() > 0)
                {
                    query.setFirstResult(params.getFirstResult());
                }
                if (params.getMaxResults() > 0)
                {
                    query.setMaxResults(params.getMaxResults());
                    sit.initializePaging(Math.max(params.getFirstResult(), 0)
                            / params.getMaxResults(), (count.intValue()
                            + params.getMaxResults() - 1)
                            / params.getMaxResults());
                }
                else
                {
                    sit.initializePaging(0, 1);
                }

                // we assume that the query returns objects of the expected type
                @SuppressWarnings("unchecked")
                List<D> results = (List<D>) query.getResultList();
                return results;
            }
        };

        return template.execute();
    }

    /**
     * Executes an iteration of a chunk search. This method loads a chunk of
     * data and applies the filter to it.
     *
     * @param <E> the type of entities to be loaded
     * @param <D> the data objects returned by this search
     * @param params the current search parameters
     * @param searchIterator the search iterator
     * @param filter the filter
     * @param queryStr the query string
     * @return the results of the search
     */
    private <E, D> SearchResult<D> executeChunkSearch(
            final MediaSearchParameters params,
            final SearchIterator searchIterator,
            final SearchFilter<E, D> filter, final String queryStr)
    {
        JPATemplate<SearchResult<D>> templ = new JPATemplate<SearchResult<D>>()
        {
            @Override
            protected SearchResult<D> performOperation(EntityManager em)
            {
                int resultSize = params.getMaxResults();
                if (resultSize <= 0)
                {
                    resultSize = Integer.MAX_VALUE;
                }
                List<D> resultList = new LinkedList<D>();

                SearchIteratorImpl sit =
                        initializeSearchIterator(em, queryStr, searchIterator);
                Query query =
                        prepareSearchQuery(em, queryStr, sit.getSearchKey());
                // the query string should result objects of the correct type
                @SuppressWarnings("unchecked")
                List<E> resultSet =
                        query.setMaxResults(getChunkSize()).getResultList();
                E lastEntity = null;
                Iterator<E> it = resultSet.iterator();

                while (it.hasNext() && resultList.size() < resultSize)
                {
                    lastEntity = it.next();
                    D data = filter.accepts(lastEntity);
                    if (data != null)
                    {
                        resultList.add(data);
                    }
                }

                sit.setHasNext(it.hasNext()
                        || resultSet.size() >= getChunkSize());
                if (lastEntity != null)
                {
                    sit.setSearchKey(filter.extractSearchKey(lastEntity));
                }

                return new SearchResultImpl<D>(resultList, sit, params);
            }
        };

        return templ.execute();
    }

    /**
     * Generates a count query for the specified query string. This method
     * replaces the projection part of the query with a {@code count()}
     * expression.
     *
     * @param query the original query
     * @return the corresponding count query
     */
    private static String createCountQuery(String query)
    {
        return query.replace(SELECT_ENTITY, SELECT_COUNT);
    }
}
