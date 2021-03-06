/*
 * Copyright 2016 Kejun Xia
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.shipdream.lib.android.mvc;

import com.shipdream.lib.android.mvc.manager.NavigationManager;
import com.shipdream.lib.android.mvc.controller.internal.AsyncTask;
import com.shipdream.lib.android.mvc.controller.internal.BaseControllerImpl;
import com.shipdream.lib.android.mvc.manager.internal.Navigator;
import com.shipdream.lib.android.mvc.manager.internal.Preparer;
import com.shipdream.lib.android.mvc.event.bus.EventBus;
import com.shipdream.lib.android.mvc.event.bus.annotation.EventBusC;
import com.shipdream.lib.android.mvc.event.bus.annotation.EventBusV;
import com.shipdream.lib.android.mvc.event.bus.internal.EventBusImpl;
import com.shipdream.lib.poke.Component;
import com.shipdream.lib.poke.Consumer;
import com.shipdream.lib.poke.Graph;
import com.shipdream.lib.poke.ImplClassLocator;
import com.shipdream.lib.poke.ImplClassLocatorByPattern;
import com.shipdream.lib.poke.ImplClassNotFoundException;
import com.shipdream.lib.poke.Provider;
import com.shipdream.lib.poke.Provider.OnFreedListener;
import com.shipdream.lib.poke.ProviderByClassType;
import com.shipdream.lib.poke.ProviderFinderByRegistry;
import com.shipdream.lib.poke.Provides;
import com.shipdream.lib.poke.ScopeCache;
import com.shipdream.lib.poke.SimpleGraph;
import com.shipdream.lib.poke.exception.CircularDependenciesException;
import com.shipdream.lib.poke.exception.PokeException;
import com.shipdream.lib.poke.exception.ProvideException;
import com.shipdream.lib.poke.exception.ProviderConflictException;
import com.shipdream.lib.poke.exception.ProviderMissingException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * {@link MvcGraph} injects instances and all its nested dependencies to target object
 * recursively. By default, all injected instances and their dependencies that are located by
 * naming convention will be <b>SINGLETON</b>. It can also register custom injections by
 * {@link #register(Component)}.
 * <p/>
 * Priority of finding implementation of contract is by<br>
 * <ol>
 * <li>Registered implementation by {@link #register(Component)}</li>
 * <li>When the injecting type is an interface: Implementation for interface a.b.c.SomeContract
 * should be named as a.b.c.internal.SomeContractImpl. Interface: a.b.c.SomeContract -->
 * a.b.c.<b>internal</b>.SomeContract<b>Impl</b></li>
 * <li>When the injecting type is a concrete class type with empty constructor: implementation is
 * itself. Therefore a new instance of itself will be created for the injection.</li>
 * <li>Otherwise, errors will occur</li>
 * </ol>
 * <p/>
 * As described above, explicit implementation can be registered by {@link #register(Component)}.
 * Once an implementation is registered, it will override the default auto implementation locating
 * described above. This would be handy in unit testing where if partial of real implementations
 * are wanted to be used and the other are mocks.<br>
 * <p/>
 * <p>Note that, <b>qualifier will be ignore for dependencies injected by naming convention
 * strategy</b>, though qualifier of provide methods of registered {@link Component} will still
 * be taken into account.
 * <p/>
 */
public class MvcGraph {
    private Logger logger = LoggerFactory.getLogger(getClass());
    ScopeCache singletonScopeCache;
    DefaultProviderFinder defaultProviderFinder;
    List<MvcBean> mvcBeans = new ArrayList<>();

    //Composite graph to hide methods
    Graph graph;

    public MvcGraph(BaseDependencies baseDependencies)
            throws ProvideException, ProviderConflictException {
        singletonScopeCache = new ScopeCache();
        defaultProviderFinder = new DefaultProviderFinder(MvcGraph.this);
        defaultProviderFinder.register(new __Component(singletonScopeCache, baseDependencies));

        graph = new SimpleGraph(defaultProviderFinder);

        graph.registerProviderFreedListener(new OnFreedListener() {
            @Override
            public void onFreed(Provider provider) {
                Object obj = provider.findCachedInstance();

                if (obj != null) {
                    //When the cached instance is still there free and dispose it.
                    if (obj instanceof MvcBean) {
                        MvcBean bean = (MvcBean) obj;
                        bean.onDisposed();
                        mvcBeans.remove(obj);

                        logger.trace("--MvcBean freed - '{}'.",
                                obj.getClass().getSimpleName());
                    }
                }
            }

        });
    }

    /**
     * For testing to hijack the cache
     * @param singletonScopeCache the cache to hijack
     */
    void hijack(ScopeCache singletonScopeCache) {
        this.singletonScopeCache = singletonScopeCache;
    }

    /**
     * Register {@link Graph.Monitor} which will be called the graph is about to inject or release an object
     *
     * @param monitor The monitor
     */
    public void registerMonitor(Graph.Monitor monitor) {
        graph.registerMonitor(monitor);
    }

    /**
     * Register {@link Graph.Monitor} which will be called the graph is about to inject or release an object
     *
     * @param monitor The monitor
     */
    public void unregisterMonitor(Graph.Monitor monitor) {
        graph.unregisterMonitor(monitor);
    }

    /**
     * Clear {@link Graph.Monitor} which will be called the graph is about to inject or release an object
     */
    public void clearMonitors() {
        graph.clearMonitors();
    }

    /**
     * Register {@link OnFreedListener} which will be called when the last cached
     * instance of an injected contract is freed.
     *
     * @param onProviderFreedListener The listener
     */
    public void registerProviderFreedListener(OnFreedListener onProviderFreedListener) {
        graph.registerProviderFreedListener(onProviderFreedListener);
    }

    /**
     * Unregister {@link OnFreedListener} which will be called when the last cached
     * instance of an injected contract is freed.
     *
     * @param onProviderFreedListener The listener
     */
    public void unregisterProviderFreedListener(OnFreedListener onProviderFreedListener) {
        graph.unregisterProviderFreedListener(onProviderFreedListener);
    }

    /**
     * Clear {@link OnFreedListener}s which will be called when the last cached
     * instance of an injected contract is freed.
     */
    public void clearOnProviderFreedListeners() {
        graph.clearOnProviderFreedListeners();
    }

    /**
     * Reference an injectable object and retain it. Use
     * {@link #dereference(Object, Class, Annotation)} to dereference it when it's not used
     * any more.
     * @param type the type of the object
     * @param qualifier the qualifier
     * @return
     */
    public <T> T reference(Class<T> type, Annotation qualifier)
            throws ProviderMissingException, ProvideException, CircularDependenciesException {
        return graph.reference(type, qualifier, Inject.class);
    }

    /**
     * Dereference an injectable object. When it's not referenced by anything else after this
     * dereferencing, release its cached instance if possible.
     * @param type the type of the object
     * @param qualifier the qualifier
     */
    public <T> void dereference(T instance, Class<T> type, Annotation qualifier)
            throws ProviderMissingException {
        graph.dereference(instance, type, qualifier, Inject.class);
    }

    /**
     * Same as {@link #use(Class, Annotation, Consumer)} except using un-qualified injectable type.
     * @param type The type of the injectable instance
     * @param consumer Consume to use the instance
     */
    public <T> void use(final Class<T> type, final Consumer<T> consumer) {
        try {
            graph.use(type, Inject.class, consumer);
        } catch (PokeException e) {
            throw new MvcGraphException(e.getMessage(), e);
        }
    }

    /**
     * Use an injectable instance in the scope of {@link Consumer#consume(Object)} without injecting
     * it as a field of an object. This method will automatically retain the instance before
     * {@link Consumer#consume(Object)} is called and released after it's returned. As a result,
     * it doesn't hold the instance like the field marked by {@link Inject} that will retain the
     * reference of the instance until {@link #release(Object)} is called. However, in the
     * scope of {@link Consumer#consume(Object)} the instance will be held.
     *
     * <p>For example,</p>
     * <pre>
        interface Os {
        }

        static class DeviceComponent extends Component {
            @Provides
            @Singleton
            public Os provide() {
                return new Os(){
                };
            }
        }
    
        class Device {
            @Inject
            private Os os;
        }

        mvcGraph.register(new DeviceComponent());

        //OsReferenceCount = 0
        mvcGraph.use(Os.class, null, new Consumer<Os>() {
            @Override
            public void consume(Os instance) {
                //First time to create the instance.
                //OsReferenceCount = 1
            }
        });
        //Reference count decremented by use method automatically
        //OsReferenceCount = 0

        final Device device = new Device();
        mvcGraph.inject(device);  //OsReferenceCount = 1
        //New instance created and cached

        mvcGraph.use(Os.class, null, new Consumer<Os>() {
            @Override
            public void consume(Os instance) {
                //Since reference count is greater than 0, cached instance will be reused
                //OsReferenceCount = 2
                Assert.assertTrue(device.os == instance);
            }
        });
        //Reference count decremented by use method automatically
        //OsReferenceCount = 1

        mvcGraph.release(device);  //OsReferenceCount = 0
        //Last instance released, so next time a new instance will be created

        mvcGraph.use(Os.class, null, new Consumer<Os>() {
            @Override
            public void consume(Os instance) {
                //OsReferenceCount = 1
                //Since the cached instance is cleared, the new instance is a newly created one.
                Assert.assertTrue(device.os != instance);
            }
        });
        //Reference count decremented by use method automatically
        //OsReferenceCount = 0

        mvcGraph.use(Os.class, null, new Consumer<Os>() {
            @Override
            public void consume(Os instance) {
                //OsReferenceCount = 1
                //Since the cached instance is cleared, the new instance is a newly created one.
                Assert.assertTrue(device.os != instance);
            }
        });
        //Reference count decremented by use method automatically
        //OsReferenceCount = 0
        //Cached instance cleared again

        mvcGraph.use(Os.class, null, new Consumer<Os>() {
            @Override
            public void consume(Os instance) {
                //OsReferenceCount = 1
                mvcGraph.inject(device);
                //Injection will reuse the cached instance and increment the reference count
                //OsReferenceCount = 2

                //Since the cached instance is cleared, the new instance is a newly created one.
                Assert.assertTrue(device.os == instance);
            }
        });
        //Reference count decremented by use method automatically
        //OsReferenceCount = 1

        mvcGraph.release(device);  //OsReferenceCount = 0
     * </pre>
     *
     * <p><b>Note that, if navigation is involved in {@link Consumer#consume(Object)}, though the
     * instance injected is still held until consume method returns, the injected instance may
     * loose its model when the next fragment is loaded. This is because Android doesn't load
     * fragment immediately by fragment manager, instead navigation will be done in the future main
     * loop. Therefore, if the model of an injected instance needs to be carried to the next fragment
     * navigated to, use {@link NavigationManager#navigate(Object)}.{@link Navigator#with(Class, Annotation, Preparer)}</b></p>
     *
     * @param type The type of the injectable instance
     * @param qualifier Qualifier for the injectable instance
     * @param consumer Consume to use the instance
     * @throws MvcGraphException throw when there are exceptions during the consumption of the instance
     */
    public <T> void use(final Class<T> type, final Annotation qualifier, final Consumer<T> consumer) {
        try {
            graph.use(type, qualifier, Inject.class, consumer);
        } catch (PokeException e) {
            throw new MvcGraphException(e.getMessage(), e);
        }
    }

    /**
     * Inject all fields annotated by {@link Inject}. References of controllers will be
     * incremented.
     *
     * @param target The target object whose fields annotated by {@link Inject} will be injected.
     */
    public void inject(Object target) {
        try {
            graph.inject(target, Inject.class);
        } catch (PokeException e) {
            throw new MvcGraphException(e.getMessage(), e);
        }
    }

    /**
     * Release cached instances held by fields of target object. References of cache of the
     * instances will be decremented. Once the reference count of a contract type reaches 0, it will
     * be removed from the cache.
     *
     * @param target of which the object fields will be released.
     */
    public void release(Object target) {
        try {
            graph.release(target, Inject.class);
        } catch (ProviderMissingException e) {
            throw new MvcGraphException(e.getMessage(), e);
        }
    }

    /**
     * Register all providers listed by the {@link Component}
     *
     * @param component The component
     */
    public void register(Component component) {
        try {
            defaultProviderFinder.register(component);
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    /**
     * Unregister all providers listed by the {@link Component}
     *
     * @param component The component
     */
    public void unregister(Component component) {
        defaultProviderFinder.unregister(component);
    }

    /**
     * Save model of all injected objects
     * @param modelKeeper The model keeper managing the model
     */
    public void saveAllModels(ModelKeeper modelKeeper) {
        int size = mvcBeans.size();
        for (int i = 0; i < size; i++) {
            MvcBean bean = mvcBeans.get(i);
            modelKeeper.saveModel(bean.getModel(), bean.modelType());
        }
    }

    /**
     * Restore model of all injected objects
     * @param modelKeeper The model keeper managing the model
     */
    @SuppressWarnings("unchecked")
    public void restoreAllModels(ModelKeeper modelKeeper) {
        int size = mvcBeans.size();
        for (int i = 0; i < size; i++) {
            MvcBean bean = mvcBeans.get(i);
            Object model = modelKeeper.retrieveModel(bean.modelType());
            if(model != null) {
                mvcBeans.get(i).restoreModel(model);
            }
        }
    }

    /**
     * Dependencies for all controllers
     */
    public abstract static class BaseDependencies {
        /**
         * Create a new instance of EventBus for events among controllers. This event bus will be
         * injected into fields annotated by {@link EventBusC}.
         *
         * @return The event bus
         */
        protected EventBus createEventBusC() {
            return new EventBusImpl();
        }

        /**
         * Create a new instance of EventBus for events posted to views. This event bus
         * will be injected into fields annotated by {@link EventBusV}.
         *
         * @return The event bus
         */
        protected EventBus createEventBusV() {
            return new EventBusImpl();
        }

        /**
         * Create a new instance of ExecutorService to support
         * {@link BaseControllerImpl#runAsyncTask(Object, AsyncTask)}. To run tasks really
         * asynchronously by calling {@link BaseControllerImpl#runAsyncTask(Object, AsyncTask)}, an
         * {@link ExecutorService} runs tasks on threads different from the caller of
         * {@link BaseControllerImpl#runAsyncTask(Object, AsyncTask)} is needed. However, to provide
         * a {@link ExecutorService} runs tasks on the same thread would be handy for testing. For
         * example, network responses can be mocked to return immediately.
         *
         * @return The {@link ExecutorService} controls on which threads tasks sent to
         * {@link BaseControllerImpl#runAsyncTask(Object, AsyncTask)} will be running on.
         */
        protected abstract ExecutorService createExecutorService();
    }

    /**
     * Internal use. Do use this in your code.
     */
    public static class __Component extends Component {
        private final BaseDependencies baseDependencies;

        public __Component(ScopeCache scopeCache, BaseDependencies baseDependencies) {
            super(scopeCache);
            this.baseDependencies = baseDependencies;
        }

        @Provides
        @EventBusC
        @Singleton
        public EventBus providesEventBusC() {
            return baseDependencies.createEventBusC();
        }

        @Provides
        @EventBusV
        @Singleton
        public EventBus providesEventBusV() {
            return baseDependencies.createEventBusV();
        }

        @Provides
        @Singleton
        public ExecutorService providesExecutorService() {
            return baseDependencies.createExecutorService();
        }
    }

    static class DefaultProviderFinder extends ProviderFinderByRegistry {
        private final MvcGraph mvcGraph;
        private final ImplClassLocator defaultImplClassLocator;
        private Map<Class, Provider> providers = new HashMap<>();

        private DefaultProviderFinder(MvcGraph mvcGraph) {
            this.mvcGraph = mvcGraph;
            defaultImplClassLocator = new ImplClassLocatorByPattern(mvcGraph.singletonScopeCache);
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> Provider<T> findProvider(Class<T> type, Annotation qualifier) throws ProviderMissingException {
            Provider<T> provider = super.findProvider(type, qualifier);
            if (provider == null) {
                provider = providers.get(type);
                if (provider == null) {
                    try {
                        Class<? extends T> impClass;
                        if (type.isInterface()) {
                            impClass = defaultImplClassLocator.locateImpl(type);
                        } else {
                            //The type is a class then it's a construable by itself.
                            impClass = type;
                        }

                        provider = new MvcProvider<>(mvcGraph.mvcBeans, type, impClass);
                        provider.setScopeCache(defaultImplClassLocator.getScopeCache());
                        providers.put(type, provider);
                    } catch (ImplClassNotFoundException e) {
                        throw new ProviderMissingException(type, qualifier, e);
                    }
                }
            }
            return provider;
        }
    }

    private static class MvcProvider<T> extends ProviderByClassType<T> {
        private final Logger logger = LoggerFactory.getLogger(MvcGraph.class);
        private List<MvcBean> mvcBeans;

        public MvcProvider(List<MvcBean> mvcBeans, Class<T> type, Class<? extends T> implementationClass) {
            super(type, implementationClass);
            this.mvcBeans = mvcBeans;
        }

        @SuppressWarnings("unchecked")
        @Override
        public T createInstance() throws ProvideException {
            final T newInstance = (T) super.createInstance();

            registerOnInjectedListener(new OnInjectedListener() {
                @Override
                public void onInjected(Object object) {
                    if (object instanceof MvcBean) {
                        MvcBean bean = (MvcBean) object;
                        bean.onConstruct();

                        logger.trace("++MvcBean injected - '{}'.",
                                object.getClass().getSimpleName());
                    }
                    unregisterOnInjectedListener(this);
                }
            });

            if (newInstance instanceof MvcBean) {
                mvcBeans.add((MvcBean) newInstance);
            }

            return newInstance;
        }
    }

}
