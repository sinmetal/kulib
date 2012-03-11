package net.sue445.kulib.service;

import java.util.logging.Level;
import java.util.logging.Logger;

import net.sue445.kulib.model.Slim3Model;

import org.slim3.datastore.Datastore;
import org.slim3.memcache.Memcache;

import com.google.appengine.api.datastore.Key;
import com.google.apphosting.api.ApiProxy;

/**
 * Datasrore access proxy service with Memcache
 * @author sue445
 *
 * @param <M>	ModelClass
 */
public class ProxyDatastoreService<M extends Slim3Model> {
	private static final Logger logger = Logger.getLogger(ProxyDatastoreService.class.getName());

	private final Class<M> modelClass;


	public ProxyDatastoreService(Class<M> modelClass){
		this.modelClass = modelClass;
	}

	/**
	 * put model and clear Memcache
	 * @param model
	 */
	public void put(M model){
		String memcacheKey = createMemcacheKey(model.getKey());
		deleteInMemcache(memcacheKey);

		Datastore.putAsync(model);
	}

	/**
	 *
	 * @param key
	 * @return
	 */
	// package private
	String createMemcacheKey(Key key){
		return getCurrentVersionId() + key.toString();
	}

	/**
	 * get current version ID (auto generated by appengine)
	 * @return
	 */
	private String getCurrentVersionId() {
		return ApiProxy.getCurrentEnvironment().getVersionId();
	}

	private void deleteInMemcache(String memcacheKey) {
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
			logger.log(Level.SEVERE, "get from Memcache: key=" + key);
			return memcacheModel;
		}

		M datastoreModel = getOrNullFromDatastore(key);
		if(datastoreModel == null){
			return null;
		}

		putToMemcache(key, datastoreModel);

		logger.log(Level.SEVERE, "get from Datastore: key=" + key);
		return datastoreModel;
	}

	/**
	 *
	 * @param memcacheKey
	 * @return
	 */
	private M getFromMemcache(Key key) {
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
	 * get model from Datastore
	 * @param key
	 * @return
	 */
	public M getOrNullFromDatastore(Key key) {
		return Datastore.getOrNull(modelClass, key);
	}

	/**
	 *
	 * @param model
	 */
	private void putToMemcache(Key key, M model) {
		String memcacheKey = createMemcacheKey(key);
		try {
			Memcache.put(memcacheKey, model);

		} catch (Exception e) {
			String message = "[FAILED]Memcache get:key=" + memcacheKey;
			logger.log(Level.WARNING, message, e);
		}
	}
}