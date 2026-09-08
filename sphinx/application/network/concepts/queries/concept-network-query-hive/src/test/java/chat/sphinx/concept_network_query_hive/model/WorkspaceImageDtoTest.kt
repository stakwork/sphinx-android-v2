package chat.sphinx.concept_network_query_hive.model

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class WorkspaceImageDtoTest {

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    @Test
    fun `WorkspaceImageDto deserializes camelCase JSON correctly`() {
        val json = """{"presignedUrl":"https://s3.example.com/logo.png?X-Amz-Expires=3600","expiresIn":3600}"""
        val adapter = moshi.adapter(WorkspaceImageDto::class.java)
        val dto = adapter.fromJson(json)

        assertNotNull(dto)
        assertEquals(
            "https://s3.example.com/logo.png?X-Amz-Expires=3600",
            dto!!.presignedUrl
        )
        assertEquals(3600L, dto.expiresIn)
    }
}
