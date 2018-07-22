/**
 * Copyright 2006 Felix Berger
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.xnap.commons.maven.gettext;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.IOUtil;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.StringTokenizer;

// TODO this is a copy from the gettext-ant-tasks. Ideally, both projects
// should use a common library
public class GettextUtils {

    public static String getJavaLocale(String locale) {
        if (locale == null) {
            throw new IllegalArgumentException();
        }
        
        List<String> tokens = new ArrayList<>(3);
        StringTokenizer t = new StringTokenizer(locale, "_");
        while (t.hasMoreTokens()) {
            tokens.add(t.nextToken());
        }
        
        if (tokens.size() < 1 || tokens.size() > 3) {
            throw new IllegalArgumentException("Invalid locale format: " + locale);
        }

        if (tokens.size() < 3) {
            // check for variant
            String lastToken = tokens.get(tokens.size() - 1);
            int index = lastToken.indexOf("@");
            if (index != -1) {
                tokens.remove(tokens.size() - 1);
                tokens.add(lastToken.substring(0, index));
                if (tokens.size() == 1) {
                    // no country code was provided, but a variant
                    tokens.add("");
                }
                tokens.add(lastToken.substring(index + 1));
            }
        }

        // Locale.java replaces these codes, so we have to do it too
        String language = tokens.get(0);
        if (language.equalsIgnoreCase("he")) {
        	tokens.set(0, "iw");
        } else if (language.equalsIgnoreCase("yi")) {
        	tokens.set(0, "ji");
        } else if (language.equalsIgnoreCase("id")) {
        	tokens.set(0, "in");
        }

        StringBuilder sb = new StringBuilder();
        for (Iterator it = tokens.iterator(); it.hasNext();) {
            String token = (String) it.next();
            sb.append(token);
            if (it.hasNext()) {
                sb.append("_");
            }
        }
        
        return sb.toString();
    }

    static void removePotCreationDate(File file, Log log) throws MojoExecutionException {
        // cannot use Strings here since file encoding is written in the file contents via
        // Content-Type: text/plain; charset=... header
        // That is why byte[] is used to process the file
        log.info("Removing POT-Creation-Date from " + file.getName());
        InputStream is = null;
        byte[] contents;
        try {
            is = new FileInputStream(file);
            contents = IOUtil.toByteArray(is);
        } catch (IOException e) {
            throw new MojoExecutionException("Unable to read " + file, e);
        } finally {
            IOUtil.close(is);
        }

        byte[] potCreationgDate;
        potCreationgDate = "POT-Creation-Date:".getBytes(StandardCharsets.UTF_8);

        int headerStart = 0, headerEnd = 0;

        searchHeader:
        for (int i = 0; i < contents.length; i++) {
            for (int j = 0; j < potCreationgDate.length; j++) {
                if (contents[i + j] != potCreationgDate[j]) {
                    continue searchHeader;
                }
            }

            // header detected
            headerStart = i;
            for (headerEnd = headerStart + 1; headerEnd < contents.length; headerEnd++) {
                if (contents[headerEnd] == '"') {
                    break;
                }
            }
        }
        OutputStream os = null;
        try {
            os = new FileOutputStream(file);
            os.write(contents, 0, headerStart);
            os.write(contents, headerEnd, contents.length - headerEnd);
        } catch (IOException e) {
            throw new MojoExecutionException("Unable to write " + file, e);
        } finally {
            IOUtil.close(os);
        }
    }

    public static void unescapeUnicode(File file, String encoding, Log log) throws MojoExecutionException {
        log.info("Unescaping unicode in " + file.getName());
        String contents;
        try {
            contents = FileUtils.fileRead(file, encoding);
        } catch (IOException e) {
            throw new MojoExecutionException("Unable to load " + file.getName(), e);
        }
        try (FileOutputStream os = new FileOutputStream(file);
             Writer w = new BufferedWriter(new OutputStreamWriter(os, encoding))
        ) {
            int length = contents.length();
            for (int i = 0; i < length; i++) {
                char c = contents.charAt(i);
                if (c == '\\'
                        && i + 1 < length && contents.charAt(i + 1) == 'u') {
                    String code = contents.substring(i + 2, i + 6);
                    w.write(Integer.parseInt(code, 16));
                    i += 5;
                } else {
                    w.append(c);
                }
            }
        } catch (IOException e) {
            throw new MojoExecutionException("Unable to write " + file, e);
        }
    }
}
