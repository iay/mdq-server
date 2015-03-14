
package uk.org.iay.mdq.server;

import static org.mockito.Mockito.*;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import net.shibboleth.metadata.Item;
import net.shibboleth.metadata.ItemSerializer;
import net.shibboleth.metadata.MockItem;
import net.shibboleth.metadata.pipeline.SimplePipeline;

import org.testng.Assert;
import org.testng.annotations.Test;

public class MetadataServiceTest {
    
    @Test
    public void testCache() throws Exception {
        // key collection
        final String id = "id";
        final List<String> key = new ArrayList<>();
        key.add(id);
        
        // first result is generation 1
        final Item<String> item1 = new MockItem("item1");
        final IdentifiedItemCollection<String> coll1 = new IdentifiedItemCollection<>(item1, key, 1);
        
        // second result is generation 2
        final Item<String> item2 = new MockItem("item2");
        final IdentifiedItemCollection<String> coll2 = new IdentifiedItemCollection<>(item2, key, 2);
        
        // Mock the item collection library
        final ItemCollectionLibrary<String> icl = mock(ItemCollectionLibrary.class);
        when(icl.get(id))
            .thenReturn(coll1, coll1, coll2, coll2);

        final MetadataService<String> service = new MetadataService<>();
        service.setId("test");
        service.setItemCollectionLibrary(icl);
        service.setRenderPipeline(new SimplePipeline<String>());
        service.setSerializer(new ItemSerializer<String>(){
            public void serialize(Item<String> item, OutputStream output) {
                try {
                    output.write(item.unwrap().getBytes());
                } catch (IOException e) {
                    // do nothing
                }
            }});
        service.initialize();
        
        // first call should get the generation 1 result
        final Result r1 = service.get(id);
        Assert.assertEquals("item1".getBytes(), r1.getRepresentation().getBytes());
        verify(icl).get(id);
        verifyNoMoreInteractions(icl);

        // second call should return the cached value
        final Result r2 = service.get(id);
        Assert.assertEquals("item1".getBytes(), r2.getRepresentation().getBytes());

        // third and fourth calls should return the generation 2 result
        final Result r3 = service.get(id);
        Assert.assertEquals("item2".getBytes(), r3.getRepresentation().getBytes());

        final Result r4 = service.get(id);
        Assert.assertEquals("item2".getBytes(), r4.getRepresentation().getBytes());
        
        // verify the interactions on the library
        verify(icl, times(4)).get(id);
        verifyNoMoreInteractions(icl);
    }
}
