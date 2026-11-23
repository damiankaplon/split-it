package pl.damiankaplon.splitit.recognition

import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.multipart
import pl.damiankaplon.splitit.IntegrationTest

/** The paths that are rejected before the model is asked. */
class RecognitionEndpointIntegrationTest : IntegrationTest() {
    @Test
    fun `requires authentication`() {
        mockMvc
            .multipart("/recognition") { file(MockMultipartFile("file", "a.jpg", "image/jpeg", byteArrayOf(1))) }
            .andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `rejects files that are not images, PDFs or CSVs`() {
        mockMvc
            .multipart("/recognition") {
                file(MockMultipartFile("file", "statements.zip", "application/zip", byteArrayOf(1)))
                with(asUser("alice"))
            }.andExpect { status { isUnsupportedMediaType() } }
    }
}
