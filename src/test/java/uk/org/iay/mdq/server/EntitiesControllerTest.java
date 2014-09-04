package uk.org.iay.mdq.server;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.w3c.dom.Element;

@ContextConfiguration({"EntitiesControllerTest-config.xml"})
@WebAppConfiguration
public class EntitiesControllerTest extends AbstractTestNGSpringContextTests {
    
    private MockMvc mockMvc;
    
    @Autowired
    private MetadataService<Element> metadataServiceMock;
    
    @Autowired
    private WebApplicationContext webApplicationContext;
    
    @BeforeMethod
    public void setUp() {
        // reset mock between tests
        Mockito.reset(metadataServiceMock);
        
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    public void entityLookupViewName() throws Exception {
        when(metadataServiceMock.get("wibble")).thenReturn(null);

        mockMvc.perform(get("/entities/wibble"))
            .andExpect(status().isOk())
            .andExpect(view().name("queryResult"));
    }
    
    @Test
    public void entitiesViewName() throws Exception {
        when(metadataServiceMock.getAll()).thenReturn(null);
        
        mockMvc.perform(get("/entities"))
            .andExpect(status().isOk())
            .andExpect(view().name("queryResult"));
    }

}
