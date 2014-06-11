import java.nio.charset.Charset

yieldUnescaped '<!DOCTYPE html>'

html {
    head {
        title('mdq-server query result')
    }
    body {
        byte[] bytes = result.getBytes()
        if (bytes == null) {
            p('No results were returned from the query.')
        } else {
            p("The query returned a $bytes.length byte response.")
            p("The ETag value for the response is ${result.getEtag()}.")
            p('Rendered metadata looks like this:')
            pre {
                yield new String(result.getBytes(), Charset.forName("UTF-8"))
            }
        }
    }
}
