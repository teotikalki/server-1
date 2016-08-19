
package com.torodb.mongodb.repl.guice;

import com.torodb.mongodb.repl.oplogreplier.fetcher.ContinuousOplogFetcher;
import com.eightkdata.mongowp.OpTime;
import com.eightkdata.mongowp.client.wrapper.MongoClientWrapper;
import com.google.common.annotations.Beta;
import com.google.common.net.HostAndPort;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import com.torodb.mongodb.repl.*;
import com.torodb.mongodb.repl.exceptions.NoSyncSourceFoundException;
import com.torodb.mongodb.repl.impl.MongoOplogReaderProvider;
import com.torodb.mongodb.repl.oplogreplier.*;
import com.torodb.mongodb.repl.oplogreplier.DefaultOplogApplier.BatchLimits;
import com.torodb.mongodb.repl.oplogreplier.analyzed.AnalyzedOpReducer;
import com.torodb.mongodb.repl.oplogreplier.batch.AnalyzedOplogBatchExecutor;
import com.torodb.mongodb.repl.oplogreplier.batch.BatchAnalyzer;
import com.torodb.mongodb.repl.oplogreplier.batch.ConcurrentOplogBatchExecutor;
import com.torodb.mongodb.repl.oplogreplier.batch.ConcurrentOplogBatchExecutor.ConcurrentOplogBatchExecutorMetrics;
import com.torodb.mongodb.utils.DbCloner;
import com.torodb.mongodb.utils.cloner.CommitHeuristic;
import java.time.Duration;
import java.util.concurrent.ThreadFactory;
import javax.annotation.Nonnull;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 *
 */
public class MongoDbReplModule extends AbstractModule {

    private static final Logger LOGGER = LogManager.getLogger(MongoDbReplModule.class);

    private final HostAndPort syncSource;
    public MongoDbReplModule(HostAndPort syncSource) {
        this.syncSource = syncSource;
    }

    @Override
    protected void configure() {
        bind(MongoClientProvider.class)
                .toInstance((hostAndPort) -> new MongoClientWrapper(hostAndPort));
        bind(OplogReaderProvider.class).to(MongoOplogReaderProvider.class).asEagerSingleton();

        bind(ReplCoordinator.ReplCoordinatorOwnerCallback.class)
                .toInstance(() -> {
                    LOGGER.error("ReplCoord has been stopped");
                }
        );

        install(new FactoryModuleBuilder()
                //To use the old applier that emulates MongoDB
//                .implement(OplogApplierService.class, SequentialOplogApplierService.class)

                //To use the applier service that delegates on a OplogApplier
                .implement(OplogApplierService.class, DefaultOplogApplierService.class)
                .build(OplogApplierService.OplogApplierServiceFactory.class)
        );

        install(new FactoryModuleBuilder()
                .implement(RecoveryService.class, RecoveryService.class)
                .build(RecoveryService.RecoveryServiceFactory.class)
        );

        install(new FactoryModuleBuilder()
                .implement(ContinuousOplogFetcher.class, ContinuousOplogFetcher.class)
                .build(ContinuousOplogFetcher.ContinuousOplogFetcherFactory.class)
        );

        bind(DbCloner.class)
                .annotatedWith(MongoDbRepl.class)
                .toProvider(AkkaDbClonerProvider.class);

        bind(OplogApplier.class)
                .to(DefaultOplogApplier.class)
                .in(Singleton.class);

        bind(DefaultOplogApplier.BatchLimits.class)
                .toInstance(new BatchLimits(1000, Duration.ofSeconds(2)));

        bind(CommitHeuristic.class)
                .to(DefaultCommitHeuristic.class)
                .in(Singleton.class);

        bind(Integer.class)
                .annotatedWith(DocsPerTransaction.class)
                .toInstance(1000);

        bind(ThreadFactory.class)
                .annotatedWith(MongoDbRepl.class)
                .toInstance(new ThreadFactoryBuilder()
                        .setNameFormat("repl-unnamed-%d")
                        .build()
                );

        bind(ConcurrentOplogBatchExecutor.class)
                .in(Singleton.class);

        bind(AnalyzedOplogBatchExecutor.class)
                .to(ConcurrentOplogBatchExecutor.class);

        bind(ConcurrentOplogBatchExecutor.ConcurrentOplogBatchExecutorMetrics.class)
                .in(Singleton.class);
        bind(AnalyzedOplogBatchExecutor.AnalyzedOplogBatchExecutorMetrics.class)
                .to(ConcurrentOplogBatchExecutorMetrics.class);

        bind(ConcurrentOplogBatchExecutor.SubBatchHeuristic.class)
                .toInstance((ConcurrentOplogBatchExecutorMetrics metrics) -> 100);


        install(new FactoryModuleBuilder()
                .implement(BatchAnalyzer.class, BatchAnalyzer.class)
                .build(BatchAnalyzer.BatchAnalyzerFactory.class)
        );
        bind(AnalyzedOpReducer.class)
                .toInstance(new AnalyzedOpReducer(false));

    }


    @Provides @Singleton
    SyncSourceProvider createSyncSourceProvider() {
        if (syncSource != null) {
            return new FollowerSyncSourceProvider(syncSource);
        }
        else {
            return new PrimarySyncSourceProvider();
        }
    }

    public static class DefaultCommitHeuristic implements CommitHeuristic {

        @Override
        public void notifyDocumentInsertionCommit(int docBatchSize, long millisSpent) {
        }

        @Override
        public int getDocumentsPerCommit() {
            return 1000;
        }

        @Override
        public boolean shouldCommitAfterIndex() {
            return false;
        }
    }

    @Beta
    private static class FollowerSyncSourceProvider implements SyncSourceProvider {
        private final HostAndPort syncSource;

        public FollowerSyncSourceProvider(@Nonnull HostAndPort syncSource) {
            this.syncSource = syncSource;
        }

        @Override
        public HostAndPort calculateSyncSource(HostAndPort oldSyncSource) {
            return syncSource;
        }

        @Override
        public HostAndPort getLastUsedSyncSource() {
            return syncSource;
        }

        @Override
        public HostAndPort getSyncSource(OpTime lastFetchedOpTime) throws
                NoSyncSourceFoundException {
            return syncSource;
        }
    }

    private static class PrimarySyncSourceProvider implements SyncSourceProvider {

        @Override
        public HostAndPort calculateSyncSource(HostAndPort oldSyncSource) throws
                NoSyncSourceFoundException {
            throw new NoSyncSourceFoundException();
        }

        @Override
        public HostAndPort getSyncSource(OpTime lastFetchedOpTime) throws
                NoSyncSourceFoundException {
            throw new NoSyncSourceFoundException();
        }

        @Override
        public HostAndPort getLastUsedSyncSource() {
            return null;
        }

    }

}
