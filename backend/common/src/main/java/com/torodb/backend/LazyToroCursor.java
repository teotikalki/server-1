
package com.torodb.backend;

import com.google.common.base.Preconditions;
import com.torodb.core.cursors.Cursor;
import com.torodb.core.cursors.ToroCursor;
import com.torodb.core.d2r.R2DTranslator;
import com.torodb.core.document.ToroDocument;
import com.torodb.core.transaction.metainf.MetaCollection;
import com.torodb.core.transaction.metainf.MetaDatabase;
import javax.annotation.Nonnull;
import org.jooq.DSLContext;

/**
 *
 */
public class LazyToroCursor implements ToroCursor {

    private final Cursor<Integer> didCursor;
    private final DefaultDocCursor docCursor;
    private boolean usedAsDocCursor = false;
    private boolean usedAsDidCursor = false;

    public LazyToroCursor(
            @Nonnull SqlInterface sqlInterface,
            @Nonnull R2DTranslator r2dTranslator,
            final @Nonnull Cursor<Integer> didCursor,
            @Nonnull DSLContext dsl,
            @Nonnull MetaDatabase metaDatabase,
            @Nonnull MetaCollection metaCollection) {
        docCursor = new DefaultDocCursor(sqlInterface, r2dTranslator, didCursor, dsl, metaDatabase, metaCollection);
        this.didCursor = didCursor;
    }

    @Override
    public Cursor<ToroDocument> asDocCursor() {
        Preconditions.checkState(!usedAsDidCursor, "This cursor has already been used as a did cursor");
        usedAsDocCursor = true;
        return docCursor;
    }

    @Override
    public Cursor<Integer> asDidCursor() {
        Preconditions.checkState(!usedAsDocCursor, "This cursor has already been used as a doc cursor");
        usedAsDidCursor = true;
        return didCursor;
    }
}
