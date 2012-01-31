/*
 * Copyright 2009-2010 by The Regents of the University of California
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * you may obtain a copy of the License from
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package edu.uci.ics.hyracks.storage.am.lsm.btree.util;

import java.util.TreeSet;

import edu.uci.ics.hyracks.api.dataflow.value.IBinaryComparatorFactory;
import edu.uci.ics.hyracks.api.dataflow.value.ISerializerDeserializer;
import edu.uci.ics.hyracks.api.dataflow.value.ITypeTraits;
import edu.uci.ics.hyracks.dataflow.common.util.SerdeUtils;
import edu.uci.ics.hyracks.storage.am.btree.tests.CheckTuple;
import edu.uci.ics.hyracks.storage.am.btree.tests.OrderedIndexTestContext;
import edu.uci.ics.hyracks.storage.am.common.api.ITreeIndex;
import edu.uci.ics.hyracks.storage.am.lsm.btree.impls.LSMBTree;
import edu.uci.ics.hyracks.storage.am.lsm.common.freepage.InMemoryBufferCache;
import edu.uci.ics.hyracks.storage.am.lsm.common.freepage.InMemoryFreePageManager;
import edu.uci.ics.hyracks.storage.common.buffercache.IBufferCache;
import edu.uci.ics.hyracks.storage.common.file.IFileMapProvider;

@SuppressWarnings("rawtypes")
public final class LSMBTreeTestContext extends OrderedIndexTestContext {    
    
    public LSMBTreeTestContext(ISerializerDeserializer[] fieldSerdes, ITreeIndex treeIndex) {
        super(fieldSerdes, treeIndex);
    }

    @Override
    public int getKeyFieldCount() {
        LSMBTree lsmTree = (LSMBTree) treeIndex;
        return lsmTree.getComparatorFactories().length;
    }

    @Override
    public IBinaryComparatorFactory[] getComparatorFactories() {
        LSMBTree lsmTree = (LSMBTree) treeIndex;
        return lsmTree.getComparatorFactories();
    }

    /**
     * Override to provide upsert semantics for the check tuples.
     */
    @Override
    public void insertCheckTuple(CheckTuple checkTuple, TreeSet<CheckTuple> checkTuples) {        
        if (checkTuples.contains(checkTuple)) {
            checkTuples.remove(checkTuple);
        }
        checkTuples.add(checkTuple);
    }
    
    public static LSMBTreeTestContext create(InMemoryBufferCache memBufferCache,
            InMemoryFreePageManager memFreePageManager, String onDiskDir, IBufferCache diskBufferCache,
            IFileMapProvider diskFileMapProvider, ISerializerDeserializer[] fieldSerdes, int numKeyFields, int fileId)
            throws Exception {
        ITypeTraits[] typeTraits = SerdeUtils.serdesToTypeTraits(fieldSerdes);
        IBinaryComparatorFactory[] cmpFactories = SerdeUtils.serdesToComparatorFactories(fieldSerdes, numKeyFields);
        LSMBTree lsmTree = LSMBTreeUtils.createLSMTree(memBufferCache, memFreePageManager, onDiskDir, diskBufferCache,
                diskFileMapProvider, typeTraits, cmpFactories);
        lsmTree.create(fileId);
        lsmTree.open(fileId);
        LSMBTreeTestContext testCtx = new LSMBTreeTestContext(fieldSerdes, lsmTree);
        return testCtx;
    }
}
