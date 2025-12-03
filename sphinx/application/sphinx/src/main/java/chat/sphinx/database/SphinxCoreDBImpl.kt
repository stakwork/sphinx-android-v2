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
import io.matthewnelson.crypto_common.extensions.toByteArray
import net.zetetic.database.sqlcipher.SQLiteDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
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
                        // Configure basic settings that can use execSQL
                        try {
                            db.execSQL("PRAGMA foreign_keys = ON")
                        } catch (e: Exception) {
                            android.util.Log.w("SphinxDB", "Failed to enable foreign keys", e)
                        }
                    }
                }
            )
        } else {
            @OptIn(RawPasswordAccess::class)
            val passphraseChars: CharArray = encryptionKey.privateKey.value
            val passphrase: ByteArray = String(passphraseChars).toByteArray(Charsets.UTF_8)

            val factory = SupportOpenHelperFactory(passphrase)

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
                        try {
                            db.execSQL("PRAGMA foreign_keys = ON")
                        } catch (e: Exception) {
                            android.util.Log.w("SphinxDB", "Failed to enable foreign keys", e)
                        }
                    }
                }
            )
        }
    }

    private fun optimizeDatabase(db: SupportSQLiteDatabase) {
        // Configuration PRAGMAs that need to use query() instead of execSQL()
        val configPragmas = mapOf(
            "journal_mode" to "WAL",
            "synchronous" to "NORMAL",
            "cache_size" to "-10000",
            "temp_store" to "MEMORY",
            "mmap_size" to "67108864",
            "auto_vacuum" to "INCREMENTAL",
            "busy_timeout" to "5000"
        )

        // Execute configuration PRAGMAs using query() method
        configPragmas.forEach { (pragma, value) ->
            try {
                val cursor = db.query("PRAGMA $pragma = $value")
                if (cursor.moveToFirst()) {
                    val result = cursor.getString(0)
                    android.util.Log.d("SphinxDB", "Set $pragma = $value, result: $result")
                }
                cursor.close()
            } catch (e: Exception) {
                android.util.Log.w("SphinxDB", "Failed to execute: PRAGMA $pragma = $value", e)
            }
        }

        // Execute maintenance PRAGMAs
        val maintenancePragmas = listOf(
            "PRAGMA optimize",
            "PRAGMA incremental_vacuum(1000)"
        )

        maintenancePragmas.forEach { pragma ->
            try {
                val cursor = db.query(pragma)
                cursor.moveToFirst() // Execute the pragma
                cursor.close()
                android.util.Log.d("SphinxDB", "Executed: $pragma")
            } catch (e: Exception) {
                android.util.Log.w("SphinxDB", "Failed to execute: $pragma", e)
            }
        }

        // Verify WAL mode is enabled
        try {
            val cursor = db.query("PRAGMA journal_mode")
            if (cursor.moveToFirst()) {
                val journalMode = cursor.getString(0)
                if (journalMode.equals("wal", ignoreCase = true)) {
                    android.util.Log.d("SphinxDB", "✅ WAL mode successfully enabled")
                } else {
                    android.util.Log.w("SphinxDB", "⚠️ WAL mode not enabled, current: $journalMode")
                }
            }
            cursor.close()
        } catch (e: Exception) {
            android.util.Log.w("SphinxDB", "Could not verify journal mode", e)
        }
    }
}
