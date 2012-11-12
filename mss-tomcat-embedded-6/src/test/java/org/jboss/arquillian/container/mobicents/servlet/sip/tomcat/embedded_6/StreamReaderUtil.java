package org.jboss.arquillian.container.mobicents.servlet.sip.tomcat.embedded_6;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

public class StreamReaderUtil {
    public static String readAllAndClose(InputStream is) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            int read;
            while ((read = is.read()) != -1) {
                out.write(read);
            }
        } finally {
            try {
                is.close();
            } catch (Exception e) {
            }
        }
        return out.toString();
    }
}
