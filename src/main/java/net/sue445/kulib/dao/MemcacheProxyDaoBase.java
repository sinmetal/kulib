package net.sue445.kulib.dao;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sue445.kulib.model.Slim3Model;

import org.slim3.datastore.Datastore;
import org.slim3.datastore.DatastoreUtil;
import org.slim3.datastore.ModelMeta;
import org.slim3.memcache.Memcache;

import com.google.appengine.api.datastore.Key;
import com.google.apphosting.api.ApiProxy;

/**
 * Datasrore access proxy dao with Memcache
 * @author sue445
 *
 * @param <M>	ModelClass
 */
public abstract class MemcacheProxyDaoBase<M extends Slim3Model> {
	protected static final Logger logger = Logger.getLogger(MemcacheProxyDaoBase.class.getName());

	/**
	 * The model class.
	 */
	protected Class<M> modelClass;

	/**
	 * The meta data of model.
	 */
	protected ModelMeta<M> m;

	/**
	 * Constructor.
	 */
	@SuppressWarnings("unchecked")
	public MemcacheProxyDaoBase() {
		for (Class<?> c = getClass(); c != Object.class; c = c.getSuperclass()) {
			Type type = c.getGenericSuperclass();
			if (type instanceof ParameterizedType) {
				modelClass =
					((Class<M>) ((ParameterizedType) type)
						.getActualTypeArguments()[0]);
				break;
			}
		}
		if (modelClass == null) {
			throw new IllegalStateException("No model class is found.");
		}
		m = DatastoreUtil.getModelMeta(modelClass);
	}

	/**
	 * put model and clear Memcache
	 * @param model
	 */
	public void put(M model){
		deleteInMemcache(model.getKey());

		Datastore.putAsync(model);
	}

	/**
	 *
	 * @param key
	 * @return
	 */
	protected String createMemcacheKey(Key key){
		return getCurrentVersionId() + key.toString();
	}

	/**
	 * get current version ID (auto generated by appengine)
	 * @return
	 */
	protected String getCurrentVersionId() {
		return ApiProxy.getCurrentEnvironment().getVersionId();
	}

	protected void deleteInMemcache(Key key) {
		String memcacheKey = createMemcacheKey(key);
		try {
			Memcache.delete(memcacheKey);
		} catch (Exception e) {
			String message = "[FAILED]Memcache delete:key=" + memcacheKey;
			logger.log(Level.WARNING, message, e);
		}
	}

	/**
	 * get model from Memcache or Datastore.<br>
	 * if not found in Memcache, get from Datastore and put to Memcache.
	 * @param key
	 * @return
	 */
	public M get(Key key){
		M memcacheModel = getFromMemcache(key);
		if(memcacheModel != null){
			logger.log(Level.FINEST, "get from Memcache: key=" + key);
			return memcacheModel;
		}

		M datastoreModel = Datastore.getOrNull(m, key);
		if(datastoreModel == null){
			return null;
		}

		putToMemcache(datastoreModel);

		logger.log(Level.FINEST, "get from Datastore: key=" + key);
		return datastoreModel;
	}

	/**
	 *
	 * @param memcacheKey
	 * @return
	 */
	protected M getFromMemcache(Key key) {
		String memcacheKey = createMemcacheKey(key);
		try {
			return Memcache.get(memcacheKey);

		} catch (Exception e) {
			String message = "[FAILED]Memcache get:key=" + memcacheKey;
			logger.log(Level.WARNING, message, e);
			return null;
		}
	}

	/**
	 *
	 * @param model
	 */
	protected void putToMemcache(M model) {
		String memcacheKey = createMemcacheKey(model.getKey());
		try {
			Memcache.put(memcacheKey, model);

		} catch (Exception e) {
			String message = "[FAILED]Memcache get:key=" + memcacheKey;
			logger.log(Level.WARNING, message, e);
		}
	}

	/**
	 * delete models in Datastote and Memcache
	 * @param key
	 */
	public void delete(Key key){
		Datastore.deleteAsync(key);
		deleteInMemcache(key);
	}
}
