/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.asterix.common.dataflow;

import org.apache.asterix.common.api.IApplicationContext;
import org.apache.asterix.common.cluster.IGlobalRecoveryManager;
import org.apache.asterix.common.transactions.IResourceIdManager;
import org.apache.hyracks.api.application.ICCServiceContext;
import org.apache.hyracks.api.client.IHyracksClientConnection;
import org.apache.hyracks.api.job.IJobLifecycleListener;
import org.apache.hyracks.storage.am.common.api.IIndexLifecycleManagerProvider;
import org.apache.hyracks.storage.common.IStorageManager;

/**
 * Provides methods for obtaining
 * {@link org.apache.hyracks.storage.am.common.api.IIndexLifecycleManagerProvider},
 * {@link org.apache.hyracks.storage.common.IStorageManager},
 * {@link org.apache.hyracks.api.application.ICCServiceContext},
 * {@link org.apache.asterix.common.cluster.IGlobalRecoveryManager},
 * and {@link org.apache.asterix.common.library.ILibraryManager}
 * at the cluster controller side.
 */
public interface ICcApplicationContext extends IApplicationContext {

    /**
     * Returns an instance of the implementation for IIndexLifecycleManagerProvider.
     *
     * @return IIndexLifecycleManagerProvider implementation instance
     */
    public IIndexLifecycleManagerProvider getIndexLifecycleManagerProvider();

    /**
     * @return an instance which implements {@link org.apache.hyracks.storage.common.IStorageManager}
     */
    public IStorageManager getStorageManager();

    /**
     * @return an instance which implements {@link org.apache.hyracks.api.application.ICCServiceContext}
     */
    @Override
    public ICCServiceContext getServiceContext();

    /**
     * @return the global recovery manager which implements
     *         {@link org.apache.asterix.common.cluster.IGlobalRecoveryManager}
     */
    public IGlobalRecoveryManager getGlobalRecoveryManager();

    /**
     * @return the active lifecycle listener at Cluster controller
     */
    public IJobLifecycleListener getActiveLifecycleListener();

    /**
     * @return a new instance of {@link IHyracksClientConnection}
     */
    public IHyracksClientConnection getHcc();

    public IResourceIdManager getResourceIdManager();
}