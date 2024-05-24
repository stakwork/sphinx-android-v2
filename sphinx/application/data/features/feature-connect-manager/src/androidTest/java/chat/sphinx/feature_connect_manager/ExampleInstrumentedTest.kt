package chat.sphinx.feature_connect_manager

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*
import org.junit.Before
import uniffi.sphinxrs.mnemonicFromEntropy
import uniffi.sphinxrs.mnemonicToSeed
import uniffi.sphinxrs.rootSignMs
import uniffi.sphinxrs.xpubFromSeed
import java.security.SecureRandom

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    private lateinit var connectManager: ConnectManagerImpl
    private var seed: String? = null
    private val lspIp = "34.229.52.200:1883"
    private var xpub: String? = null
    private var sign: String? = null
    private val network = "regtest"

    @Before
    fun setUp() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        connectManager = ConnectManagerImpl()
        generateMnemonicAndSeed()
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    private fun generateMnemonicAndSeed() {
        val randomBytes = generateRandomBytes(16)
        val randomBytesString = randomBytes.joinToString("") { it.toString(16).padStart(2, '0') }
        val mnemonic = mnemonicFromEntropy(randomBytesString)
        assertNotNull(mnemonic)

        val generatedSeed = mnemonic.let { mnemonicToSeed(it) }
        seed = generatedSeed
        assertNotNull(seed)
    }

    @Test
    fun generateXpub() {
        assertNotNull("Seed should be generated", seed)
        val myXpub = xpubFromSeed(seed!!, getTimestampInMilliseconds(), network)
        xpub = myXpub
        assertNotNull(xpub)
    }

    @Test
    fun signMsg() {
        assertNotNull("Seed should be generated", seed)
        val generatedSign = rootSignMs(seed!!, getTimestampInMilliseconds(), network)
        sign = generatedSign
        assertNotNull(sign)
    }

    @Test
    fun useSignForAnotherMethod() {
        assertNotNull("Sign should be generated", sign)

        val anotherResult = anotherMethodThatUsesSign(sign!!)
        assertNotNull(anotherResult)
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

    private fun getTimestampInMilliseconds(): String =
        System.currentTimeMillis().toString()

    private fun anotherMethodThatUsesSign(sign: String): String {
        // Your logic here
        return "Result based on sign: $sign"
    }
}