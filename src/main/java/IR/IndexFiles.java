/*
* Licensed to the Apache Software Foundation (ASF) under one or more
003 * contributor license agreements.  See the NOTICE file distributed with
004 * this work for additional information regarding copyright ownership.
005 * The ASF licenses this file to You under the Apache License, Version 2.0
006 * (the "License"); you may not use this file except in compliance with
007 * the License.  You may obtain a copy of the License at
008 *
009 *     http://www.apache.org/licenses/LICENSE-2.0
010 *
011 * Unless required by applicable law or agreed to in writing, software
012 * distributed under the License is distributed on an "AS IS" BASIS,
013 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
014 * See the License for the specific language governing permissions and
015 * limitations under the License.
016 */
package IR;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Date;

public class IndexFiles {

    private IndexFiles() {}

    public static void main(String[] args) {
        String usage = "java org.apache.lucene.demo.IndexFiles"
                + " [-index INDEX_PATH] [-docs DOCS_PATH] [-update]\n\n"
                + "This indexes the documents in DOCS_PATH, creating a Lucene index"
                + "in INDEX_PATH that can be searched with SearchFiles";
        String indexPath = "index";
        String docsPath = null;
        String index_paath = null;
        String queries_paath = null;
        String score_me = null;
        String args_paath = null;
        boolean create = true;
        for (int i = 0; i < args.length; i++) {
            if ("-index".equals(args[i])) {
                indexPath = args[i + 1];
                index_paath = indexPath;
                i++;
            } else if ("-docs".equals(args[i])) {
                docsPath = args[i + 1];
                i++;
            } else if ("-update".equals(args[i])) {
                create = false;
            } else if ("-queries".equals(args[i])) {
                queries_paath = args[i + 1];
                i++;
            } else if ("-score".equals(args[i])) {
                score_me = args[i + 1];
                i++;
            } else if ("-args_path".equals(args[i])) {
                args_paath = args[i + 1];
                i++;
            }
        }

        if (docsPath == null) {
            System.err.println("Usage: " + usage);
            System.exit(1);
        }

        // Ensure score_me is not null
        if (score_me == null || score_me.isEmpty()) {
            System.err.println("Error: score_me cannot be null or empty");
            System.exit(1);
        }

        // Provide a default output path if args_paath is null
        if (args_paath == null || args_paath.isEmpty()) {
            args_paath = "default_output";  // Update to a valid default path on your system
        }

        final Path docDir = Paths.get(docsPath);
        if (!Files.isReadable(docDir)) {
            System.out.println("Document directory '" + docDir.toAbsolutePath() + "' does not exist or is not readable, please check the path");
            System.exit(1);
        }

        Date start = new Date();
        try {
            System.out.println("Indexing to directory '" + indexPath + "'...");

            Directory dir = FSDirectory.open(Paths.get(indexPath));
            Analyzer analyzer = new StandardAnalyzer();
            IndexWriterConfig iwc = new IndexWriterConfig(analyzer);

            if (create) {
                iwc.setOpenMode(OpenMode.CREATE);
            } else {
                iwc.setOpenMode(OpenMode.CREATE_OR_APPEND);
            }

            IndexWriter writer = new IndexWriter(dir, iwc);
            indexDocs(writer, docDir);
            writer.close();

            // Perform search
            Searcher search = new Searcher();
            search.SearchMe(index_paath, queries_paath, score_me, args_paath);
            System.out.println("\nIndexing for all 1400 cran documents were successfully done at the directory " + index_paath);
            System.out.println("Searching was successfully performed and 'outputs.txt' was created at " + args_paath);
            System.out.println("You can now use the Trec_Eval to evaluate from the above mentioned 'outputs.txt'.\n");

            Date end = new Date();
            System.out.println(end.getTime() - start.getTime() + " total milliseconds");

        } catch (IOException e) {
            System.out.println(" caught a " + e.getClass() +
                    "\n with message: " + e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    static void indexDocs(final IndexWriter writer, Path path) throws IOException {
        if (Files.isDirectory(path)) {
            Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    try {
                        indexDoc(writer, file, attrs.lastModifiedTime().toMillis());
                    } catch (IOException ignore) {
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } else {
            indexDoc(writer, path, Files.getLastModifiedTime(path).toMillis());
        }
    }

    static void indexDoc(IndexWriter writer, Path file, long lastModified) throws IOException {
        try (InputStream stream = Files.newInputStream(file)) {
            BufferedReader inputReader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            String currentLine = inputReader.readLine();
            Document document = null;
            String docType = "";

            while (currentLine != null) {
                if (currentLine.startsWith(".I")) {
                    if (document != null) {
                        if (writer.getConfig().getOpenMode() == OpenMode.CREATE) {
                            writer.addDocument(document);
                        } else {
                            writer.updateDocument(new Term("path", file.toString()), document);
                        }
                    }
                    document = new Document();
                    Field pathField = new StringField("path", currentLine, Field.Store.YES);
                    document.add(pathField);
                } else if (currentLine.startsWith(".T")) {
                    docType = "Title";
                    currentLine = inputReader.readLine();
                } else if (currentLine.startsWith(".A")) {
                    docType = "Author";
                    currentLine = inputReader.readLine();
                } else if (currentLine.startsWith(".W")) {
                    docType = "Words";
                    currentLine = inputReader.readLine();
                } else if (currentLine.startsWith(".B")) {
                    docType = "Bibliography";
                    currentLine = inputReader.readLine();
                }
                if (document != null && docType != null && !currentLine.startsWith(".I")) {
                    document.add(new TextField(docType, currentLine, Field.Store.YES));
                }
                currentLine = inputReader.readLine();
            }

            if (document != null) {
                if (writer.getConfig().getOpenMode() == OpenMode.CREATE) {
                    writer.addDocument(document);
                } else {
                    writer.updateDocument(new Term("path", file.toString()), document);
                }
            }
        }
    }
}
