import java.nio.charset.Charset

import uk.org.iay.mdq.server.Representation

yieldUnescaped '<!DOCTYPE html>'

html {
    head {
        title('mdq-server query result')
    }
    body {
        h1('mda-server query result')
        if (result.isNotFound()) {
            p('No results were returned from the query.')
        } else {
            h2('Result Returned')
            Collection<String> ids = result.getIdentifiers();
            p("Identifiers: ${ids.size}");
            if (ids.size != 0) {
                ul {
                   for (id in ids) {
                       if (id == null) {
                           li('null (ID_ALL)')
                       } else {
                           li(id)
                       }
                   }
                }
            }

            h3('Normal Representation')
            Representation norm = result.getRepresentation();
            p("The default representation has a length of ${norm.getBytes().length}.")
            p("Its ETag value is ${norm.getETag()}.")
            
            Representation gzip = result.getGZIPRepresentation();
            if (gzip != null) {
                h3('GZIP Representation')
                p("The GZIPped representation has a length of ${gzip.getBytes().length}.")
                p("Its ETag value is ${gzip.getETag()}.")
            }
            
            Representation deflate = result.getDeflateRepresentation();
            if (deflate != null) {
                h3('Deflated Representation')
                p("The deflated representation has a length of ${deflate.getBytes().length}.")
                p("Its ETag value is ${deflate.getETag()}.")
            }

            h2('Rendered Metadata')
            pre {
                yield new String(norm.getBytes(), Charset.forName("UTF-8"))
            }
        }
    }
}
