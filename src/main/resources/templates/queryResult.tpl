import java.nio.charset.Charset

yieldUnescaped '<!DOCTYPE html>'

html {
    head {
        title('mdq-server query result')
    }
    body {
        if (result.isNotFound()) {
            p('No results were returned from the query.')
        } else {
            byte[] bytes = result.getBytes()
            p("The query returned a $bytes.length byte response.")
            p("The ETag value for the response is ${result.getETag()}.")
            p('Rendered metadata looks like this:')
            pre {
                yield new String(result.getBytes(), Charset.forName("UTF-8"))
            }
        }
    }
}
