
package uk.org.iay.mdq.server;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import net.shibboleth.metadata.Item;
import net.shibboleth.metadata.ItemId;
import net.shibboleth.metadata.ItemTag;
import net.shibboleth.metadata.MockItem;
import net.shibboleth.metadata.pipeline.AbstractStage;
import net.shibboleth.metadata.pipeline.SimplePipeline;
import net.shibboleth.metadata.pipeline.Stage;
import net.shibboleth.metadata.pipeline.StageProcessingException;
import net.shibboleth.metadata.pipeline.StaticItemSourceStage;

import org.springframework.boot.actuate.health.Status;
import org.testng.Assert;
import org.testng.annotations.Test;

public class ItemCollectionLibraryTest {

    @Test
    public void testTaggedCollections() throws Exception {
        
        final Item<String> item1 = new MockItem("item1");
        item1.getItemMetadata().put(new ItemId("item1"));
        item1.getItemMetadata().put(new ItemId("item1alias"));
        item1.getItemMetadata().put(new ItemTag("odds"));
        item1.getItemMetadata().put(new ItemTag("three"));
        
        final Item<String> item2 = new MockItem("item2");
        item2.getItemMetadata().put(new ItemId("item2"));
        item2.getItemMetadata().put(new ItemId("item2alias"));
        item2.getItemMetadata().put(new ItemTag("evens"));
        item2.getItemMetadata().put(new ItemTag("three"));
        
        final Item<String> item3 = new MockItem("item3");
        item3.getItemMetadata().put(new ItemId("item3"));
        item3.getItemMetadata().put(new ItemId("item3alias"));
        item3.getItemMetadata().put(new ItemTag("odds"));
        item3.getItemMetadata().put(new ItemTag("three"));
        
        final Item<String> item4 = new MockItem("item4");
        item4.getItemMetadata().put(new ItemId("item4"));
        
        final List<Item<String>> items = new ArrayList<>();
        items.add(item1);
        items.add(item2);
        items.add(item3);
        items.add(item4);
        
        final StaticItemSourceStage<String> sos = new StaticItemSourceStage<>();
        sos.setId("staticSource");
        sos.setSourceItems(items);
        sos.initialize();
        
        final List<Stage<String>> stages = new ArrayList<>();
        stages.add(sos);
        
        final SimplePipeline<String> pipeline = new SimplePipeline<>();
        pipeline.setId("pipeline");
        pipeline.setStages(stages);
        pipeline.initialize();
        
        final ItemCollectionLibrary<String> library = new ItemCollectionLibrary<>();
        library.setId("library");
        library.setSourcePipeline(pipeline);
        library.initialize();
        
        // look at the ALL output
        Assert.assertEquals(4, library.getAll().getItems().size());
        
        // Something that doesn't exist
        Assert.assertNull(library.get("noSuchItem"));
        
        // Individual items and their aliases
        Assert.assertEquals(1, library.get("item3").getItems().size());
        Assert.assertEquals(1, library.get("item3alias").getItems().size());
        Assert.assertEquals(2, library.get("item3").getIdentifiers().size());
        
        // check size of tagged collections
        Assert.assertEquals(2, library.get("odds").getItems().size());
        Assert.assertEquals(1, library.get("odds").getIdentifiers().size());
        Assert.assertEquals(1, library.get("evens").getItems().size());
        Assert.assertEquals(3, library.get("three").getItems().size());
    }

    @Test
    public void testGenerations() throws Exception {
        final Item<String> item1 = new MockItem("item1");
        item1.getItemMetadata().put(new ItemId("item1"));

        final Item<String> item2 = new MockItem("item1");
        item2.getItemMetadata().put(new ItemId("item2"));

        final Item<String> item3 = new MockItem("item1");
        item3.getItemMetadata().put(new ItemId("item3"));

        final List<Item<String>> items = new ArrayList<>();
        items.add(item1);
        items.add(item2);
        items.add(item3);

        final StaticItemSourceStage<String> sos = new StaticItemSourceStage<>();
        sos.setId("staticSource");
        sos.setSourceItems(items);
        sos.initialize();
        
        final List<Stage<String>> stages = new ArrayList<>();
        stages.add(sos);
        
        final SimplePipeline<String> pipeline = new SimplePipeline<>();
        pipeline.setId("pipeline");
        pipeline.setStages(stages);
        pipeline.initialize();
        
        final ItemCollectionLibrary<String> library = new ItemCollectionLibrary<>();
        library.setId("library");
        library.setSourcePipeline(pipeline);
        library.initialize();
        
        // make a couple of queries on this first generation
        final IdentifiedItemCollection<String> res1 = library.get("item1");
        final IdentifiedItemCollection<String> all1 = library.get(ItemCollectionLibrary.ID_ALL);
        Assert.assertEquals(res1.getGeneration(), all1.getGeneration());
        Assert.assertEquals(1, res1.getItems().size());
        Assert.assertEquals(1, res1.getIdentifiers().size());
        Assert.assertEquals(3, all1.getItems().size());
        Assert.assertEquals(1, all1.getIdentifiers().size());
        
        // refreshing should change the generation
        library.refresh();
        final IdentifiedItemCollection<String> res2 = library.get("item1");
        final IdentifiedItemCollection<String> all2 = library.get(ItemCollectionLibrary.ID_ALL);
        Assert.assertEquals(res2.getGeneration(), all2.getGeneration());
        Assert.assertNotEquals(res1.getGeneration(), res2.getGeneration());
        Assert.assertNotEquals(all1.getGeneration(), all2.getGeneration());
        Assert.assertEquals(1, res2.getItems().size());
        Assert.assertEquals(1, res2.getIdentifiers().size());
        Assert.assertEquals(3, all2.getItems().size());
        Assert.assertEquals(1, all2.getIdentifiers().size());
    }
    
    /**
     * A stage which throws an NPE after a certain number of calls.
     *
     * @param <T> type of item to operate on
     */
    static class NPEAfterNStage<T> extends AbstractStage<T> implements Stage<T> {

        private long count;
        
        @Override
        protected void doExecute(Collection<Item<T>> itemCollection) throws StageProcessingException {
            if (count ==0) {
                throw new NullPointerException("synthetic NPE");
            }
            count--;
        }
        
        public NPEAfterNStage(final long c) {
            count = c;
        }
    }
    
    @Test
    public void testHealthRefreshing() throws Exception {
        final Duration refreshInterval = Duration.ofMillis(100); // 1/10 second

        final Item<String> item1 = new MockItem("item1");
        item1.getItemMetadata().put(new ItemId("item1"));

        final List<Item<String>> items = new ArrayList<>();
        items.add(item1);

        final StaticItemSourceStage<String> sos = new StaticItemSourceStage<>();
        sos.setId("staticSource");
        sos.setSourceItems(items);
        sos.initialize();
        
        final NPEAfterNStage<String> npes = new NPEAfterNStage<>(2);
        npes.setId("NPE");
        npes.initialize();
        
        final List<Stage<String>> stages = new ArrayList<>();
        stages.add(sos);
        stages.add(npes);
        
        final SimplePipeline<String> pipeline = new SimplePipeline<>();
        pipeline.setId("pipeline");
        pipeline.setStages(stages);
        pipeline.initialize();
        
        final ItemCollectionLibrary<String> library = new ItemCollectionLibrary<>();
        library.setId("library");
        library.setSourcePipeline(pipeline);
        library.setRefreshInterval(refreshInterval);
        Assert.assertEquals(library.health().getStatus(), Status.DOWN);
        library.initialize();
        Assert.assertEquals(library.health().getStatus(), Status.UP);
        Thread.sleep(refreshInterval.toMillis());
        Assert.assertEquals(library.health().getStatus(), Status.UP);
        Thread.sleep(3 * refreshInterval.toMillis());
        Assert.assertEquals(library.health().getStatus(), new Status("DEGRADED"));
        
        library.destroy();
        Assert.assertEquals(library.health().getStatus(), Status.DOWN);
    }

    @Test
    public void testHealthNoRefresh() throws Exception {
        final Item<String> item1 = new MockItem("item1");
        item1.getItemMetadata().put(new ItemId("item1"));

        final List<Item<String>> items = new ArrayList<>();
        items.add(item1);

        final StaticItemSourceStage<String> sos = new StaticItemSourceStage<>();
        sos.setId("staticSource");
        sos.setSourceItems(items);
        sos.initialize();
        
        final List<Stage<String>> stages = new ArrayList<>();
        stages.add(sos);
        
        final SimplePipeline<String> pipeline = new SimplePipeline<>();
        pipeline.setId("pipeline");
        pipeline.setStages(stages);
        pipeline.initialize();
        
        final ItemCollectionLibrary<String> library = new ItemCollectionLibrary<>();
        library.setId("library");
        library.setSourcePipeline(pipeline);
        Assert.assertEquals(library.health().getStatus(), Status.DOWN);
        library.initialize();
        Assert.assertEquals(library.health().getStatus(), Status.UP);
        
        library.destroy();
        Assert.assertEquals(library.health().getStatus(), Status.DOWN);
    }
}
