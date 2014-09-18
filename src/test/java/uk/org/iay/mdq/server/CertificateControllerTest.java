package uk.org.iay.mdq.server;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@ContextConfiguration({"CertificateControllerTest-config.xml"})
@WebAppConfiguration
public class CertificateControllerTest extends AbstractTestNGSpringContextTests {
    
    private MockMvc mockMvc;
    
    @Autowired
    private WebApplicationContext webApplicationContext;
    
    @BeforeMethod
    public void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    public void fetchCertificate() throws Exception {
        mockMvc.perform(get("/x-certificate"))
            .andExpect(status().isOk())
            .andExpect(content().contentType("text/plain"));
    }

}
