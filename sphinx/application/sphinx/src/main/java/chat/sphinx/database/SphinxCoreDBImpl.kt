package chat.sphinx.database

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import chat.sphinx.concept_coredb.SphinxDatabase
import chat.sphinx.feature_coredb.CoreDBImpl
import com.squareup.moshi.Moshi
import com.squareup.sqldelight.android.AndroidSqliteDriver
import com.squareup.sqldelight.db.SqlDriver
import io.matthewnelson.build_config.BuildConfigDebug
import io.matthewnelson.concept_encryption_key.EncryptionKey
import io.matthewnelson.crypto_common.annotations.RawPasswordAccess
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory
import java.io.File

class SphinxCoreDBImpl(
    context: Context,
    private val buildConfigDebug: BuildConfigDebug,
    moshi: Moshi,
): CoreDBImpl(moshi) {

    private val appContext: Context = context.applicationContext

    @Volatile
    private var driver: AndroidSqliteDriver? = null

    override fun getSqlDriver(encryptionKey: EncryptionKey): SqlDriver {
        return driver ?: synchronized(this) {
            driver ?: createSqlDriver(encryptionKey)
                .also { driver = it }
        }
    }

    private fun createSqlDriver(encryptionKey: EncryptionKey): AndroidSqliteDriver {
        // Don't encrypt the DB for debug version
        return if (buildConfigDebug.value) {
            AndroidSqliteDriver(
                SphinxDatabase.Schema,
                appContext,
                DB_NAME,
                callback = object : AndroidSqliteDriver.Callback(SphinxDatabase.Schema) {
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)

                        driver?.let {
                            optimizeDatabase(it)
                        }
                    }

                    override fun onConfigure(db: SupportSQLiteDatabase) {
                        super.onConfigure(db)
                        driver?.execute(null, "PRAGMA foreign_keys = ON", 0)
                    }
                }
            )
        } else {
            @OptIn(RawPasswordAccess::class)
            val passphrase: ByteArray = SQLiteDatabase.getBytes(encryptionKey.privateKey.value)

            @Suppress("RedundantExplicitType")
            val factory: SupportFactory = SupportFactory(passphrase, null, true)

            AndroidSqliteDriver(
                SphinxDatabase.Schema,
                appContext,
                DB_NAME,
                factory,
                callback = object : AndroidSqliteDriver.Callback(SphinxDatabase.Schema) {
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)

                        driver?.let {
                            optimizeDatabase(it)
                        }
                    }

                    override fun onConfigure(db: SupportSQLiteDatabase) {
                        super.onConfigure(db)
                        driver?.execute(null, "PRAGMA foreign_keys = ON", 0)
                    }
                }
            )
        }
    }

    private fun optimizeDatabase(driver: SqlDriver) {
        // SQLite optimizations for GrapheneOS
        driver.execute(null, "PRAGMA journal_mode = WAL", 0)
        driver.execute(null, "PRAGMA synchronous = NORMAL", 0)
        driver.execute(null, "PRAGMA cache_size = 10000", 0) // 10MB cache
        driver.execute(null, "PRAGMA temp_store = MEMORY", 0)
        driver.execute(null, "PRAGMA mmap_size = 268435456", 0) // 256MB mmap
        driver.execute(null, "PRAGMA optimize", 0)

        // GrapheneOS specific optimizations
        driver.execute(null, "PRAGMA auto_vacuum = INCREMENTAL", 0)
        driver.execute(null, "PRAGMA incremental_vacuum(1000)", 0)
    }
}
