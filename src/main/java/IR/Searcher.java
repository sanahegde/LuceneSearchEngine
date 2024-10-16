package IR;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.*;
import org.apache.lucene.store.FSDirectory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Date;
import java.util.HashMap;

public class Searcher {

    public Searcher() {}

    public void SearchMe(String index_paath, String queries_paath, String score_me, String args_paath) throws Exception {
        if (score_me == null || score_me.isEmpty()) {
            throw new IllegalArgumentException("score_me cannot be null or empty");
        }

        String index = index_paath;
        String queries = queries_paath;
        String write_path = args_paath;

        int setScore = Integer.parseInt(score_me);
        IndexReader reader = DirectoryReader.open(FSDirectory.open(Paths.get(index)));
        IndexSearcher searcher = new IndexSearcher(reader);

        switch (setScore) {
            case 0:
                searcher.setSimilarity(new ClassicSimilarity());
                break;
            case 1:
                searcher.setSimilarity(new BM25Similarity());
                break;
            case 2:
                searcher.setSimilarity(new BooleanSimilarity());
                break;
            case 3:
                searcher.setSimilarity(new LMDirichletSimilarity());
                break;
            case 4:
                searcher.setSimilarity(new LMJelinekMercerSimilarity(0.7f));
                break;
            default:
                throw new IllegalArgumentException("Invalid score_me value: " + score_me);
        }

        Analyzer analyzer = new StandardAnalyzer();
        BufferedReader in = (queries != null) ? Files.newBufferedReader(Paths.get(queries), StandardCharsets.UTF_8) :
                new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));

        HashMap<String, Float> boostedScores = new HashMap<>();
        boostedScores.put("Title", 0.65f);
        boostedScores.put("Author", 0.04f);
        boostedScores.put("Bibliography", 0.02f);
        boostedScores.put("Words", 0.35f);

        MultiFieldQueryParser parser = new MultiFieldQueryParser(
                new String[]{"Title", "Author", "Bibliography", "Words"},
                analyzer, boostedScores);

        String line = in.readLine();
        String nextLine = "";
        int queryNumber = 1;

        try (PrintWriter writer = new PrintWriter(write_path + "outputs.txt", "UTF-8")) {
            while (line != null) {
                if (line.startsWith(".I")) {
                    line = in.readLine();
                    if (line.equals(".W")) {
                        line = in.readLine();
                    }
                    nextLine = "";
                    while (!line.startsWith(".I")) {
                        nextLine = nextLine + " " + line;
                        line = in.readLine();
                        if (line == null) break;
                    }
                }

                Query query = parser.parse(QueryParser.escape(nextLine.trim()));
                doPagingSearch(queryNumber, searcher, query, writer);
                queryNumber++;
            }
        }
        reader.close();
    }

    public static void doPagingSearch(int queryNumber, IndexSearcher searcher, Query query, PrintWriter writer) throws IOException {
        TopDocs results = searcher.search(query, 100);
        ScoreDoc[] hits = results.scoreDocs;

        for (int i = 0; i < hits.length; i++) {
            Document doc = searcher.doc(hits[i].doc);
            String path = doc.get("path");
            if (path != null) {
                writer.println(queryNumber + " 0 " + path.replace(".I ", "") + " " + (i + 1) + " " + hits[i].score + " Any");
            }
        }
    }
}
