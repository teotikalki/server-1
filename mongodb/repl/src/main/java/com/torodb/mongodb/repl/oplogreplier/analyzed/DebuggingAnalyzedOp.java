/*
 * This file is part of ToroDB.
 *
 * ToroDB is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ToroDB is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with repl. If not, see <http://www.gnu.org/licenses/>.
 *
 * Copyright (C) 2016 8Kdata.
 * 
 */

package com.torodb.mongodb.repl.oplogreplier.analyzed;

import com.eightkdata.mongowp.Status;
import com.eightkdata.mongowp.server.api.oplog.CollectionOplogOperation;
import com.eightkdata.mongowp.server.api.oplog.DeleteOplogOperation;
import com.eightkdata.mongowp.server.api.oplog.InsertOplogOperation;
import com.eightkdata.mongowp.server.api.oplog.UpdateOplogOperation;
import com.torodb.kvdocument.values.KVDocument;
import com.torodb.kvdocument.values.KVValue;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 *
 */
public class DebuggingAnalyzedOp extends AnalyzedOp {

    private final AnalyzedOp delegate;
    private final List<CollectionOplogOperation> fromOps;

    public DebuggingAnalyzedOp(KVValue<?> mongoDocId) {
        this.delegate = new NoopAnalyzedOp(mongoDocId);
        this.fromOps = Collections.emptyList();
    }

    DebuggingAnalyzedOp(AnalyzedOp delegate, DebuggingAnalyzedOp other, CollectionOplogOperation currentOp) {
        this.delegate = delegate;
        this.fromOps = new ArrayList<>(other.fromOps.size() + 1);
        fromOps.addAll(other.fromOps);
        fromOps.add(currentOp);
    }

    @Override
    public AnalyzedOpType getType() {
        return delegate.getType();
    }

    public List<CollectionOplogOperation> getFromOps() {
        return fromOps;
    }

    @Override
    public KVDocument calculateDocToInsert(Function<AnalyzedOp, KVDocument> fetchedDocFun) {
        return delegate.calculateDocToInsert(op -> {
            if (delegate.equals(op)) {
                return fetchedDocFun.apply(this);
            } else {
                return fetchedDocFun.apply(op);
            }
        });
    }

    @Override
    AnalyzedOp andThenInsert(InsertOplogOperation op) {
        return new DebuggingAnalyzedOp(delegate.andThenInsert(op), this, op);
    }

    @Override
    AnalyzedOp andThenUpdateMod(UpdateOplogOperation op) {
        return new DebuggingAnalyzedOp(delegate.andThenUpdateMod(op), this, op);
    }

    @Override
    AnalyzedOp andThenUpdateSet(UpdateOplogOperation op) {
        return new DebuggingAnalyzedOp(delegate.andThenUpdateSet(op), this, op);
    }

    @Override
    AnalyzedOp andThenUpsertMod(UpdateOplogOperation op) {
        return new DebuggingAnalyzedOp(delegate.andThenUpsertMod(op), this, op);
    }

    @Override
    AnalyzedOp andThenUpsertSet(UpdateOplogOperation op) {
        return new DebuggingAnalyzedOp(delegate.andThenUpsertSet(op), this, op);
    }

    @Override
    AnalyzedOp andThenDelete(DeleteOplogOperation op) {
        return new DebuggingAnalyzedOp(delegate.andThenDelete(op), this, op);
    }

    @Override
    public KVValue<?> getMongoDocId() {
        return delegate.getMongoDocId();
    }

    @Override
    public Status<?> getMismatchErrorMessage() throws UnsupportedOperationException {
        return delegate.getMismatchErrorMessage();
    }

    @Override
    public String toString() {
        return delegate.toString() + "(from " + fromOps + ")";
    }

}
