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

                        optimizeDatabase(db)
                    }

                    override fun onConfigure(db: SupportSQLiteDatabase) {
                        super.onConfigure(db)

                        db.execSQL("PRAGMA foreign_keys = ON")
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

                        optimizeDatabase(db)
                    }

                    override fun onConfigure(db: SupportSQLiteDatabase) {
                        super.onConfigure(db)
                        db.execSQL("PRAGMA foreign_keys = ON")
                    }
                }
            )
        }
    }

    private fun optimizeDatabase(db: SupportSQLiteDatabase) {
        val configPragmas = listOf(
            "PRAGMA foreign_keys = ON",
            "PRAGMA journal_mode = WAL",
            "PRAGMA synchronous = NORMAL",
            "PRAGMA cache_size = -10000",
            "PRAGMA temp_store = MEMORY",
            "PRAGMA mmap_size = 67108864",
            "PRAGMA auto_vacuum = INCREMENTAL"
        )

        // Execute configuration PRAGMAs
        configPragmas.forEach { pragma ->
            try {
                db.execSQL(pragma)
            } catch (e: Exception) {
                android.util.Log.w("SphinxDB", "Failed to execute: $pragma", e)
            }
        }

        // Execute query PRAGMAs
        val queryPragmas = listOf(
            "PRAGMA optimize",
            "PRAGMA incremental_vacuum(1000)"
        )

        queryPragmas.forEach { pragma ->
            try {
                db.query(pragma).use { cursor ->
                    // Just execute the query, don't need to process results
                    cursor.moveToFirst()
                }
            } catch (e: Exception) {
                android.util.Log.w("SphinxDB", "Failed to execute query: $pragma", e)
            }
        }
    }
}
