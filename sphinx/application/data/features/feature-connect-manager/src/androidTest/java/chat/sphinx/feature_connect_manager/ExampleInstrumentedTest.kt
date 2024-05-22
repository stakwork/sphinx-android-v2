package chat.sphinx.feature_connect_manager

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*
import org.junit.Before
import uniffi.sphinxrs.mnemonicFromEntropy
import java.security.SecureRandom

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    private lateinit var connectManager: ConnectManagerImpl
    @Before
    fun setUp() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        connectManager = ConnectManagerImpl()
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    @Test
    fun generateMnemonic() {
        var mnemonic: String? = null
        try {
            val randomBytes = generateRandomBytes(16)
            val randomBytesString = randomBytes.joinToString("") { it.toString(16).padStart(2, '0') }
            mnemonic = mnemonicFromEntropy(randomBytesString)
        } catch (e: Exception) {
            println("Error generating mnemonic: ${e.message}")
        }
        assertNotNull(mnemonic)
    }

    private fun generateRandomBytes(size: Int): UByteArray {
        val random = SecureRandom()
        val bytes = ByteArray(size)
        random.nextBytes(bytes)
        val uByteArray = UByteArray(size)

        for (i in bytes.indices) {
            uByteArray[i] = bytes[i].toUByte()
        }

        return uByteArray
    }

}