package com.mondego.models;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.index.TieredMergePolicy;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;

import com.mondego.indexbased.SearchManager;
import com.mondego.utility.BlockInfo;

public class Shard {
    int id;
    int minMetricValue, maxMetricValue;
    int minMetricValueToIndex;
    int maxMetricValueToIndex;
    public String indexPath;
    IndexWriter invertedIndexWriter;
    IndexWriter forwardIndexWriter;
    public List<Shard> subShards;
    int size;
    private static final Logger logger = LogManager.getLogger(Shard.class);

    public Shard(int id, int minBagSizeToSearch, int maxBagSizeToSearch,
            String indexPath, boolean forWriting) {
        this.id = id;
        this.indexPath = indexPath;
        this.minMetricValue = minBagSizeToSearch;
        this.maxMetricValue = maxBagSizeToSearch;
        this.minMetricValueToIndex = BlockInfo.getMinimumSimilarityThreshold(
                minBagSizeToSearch, SearchManager.th);
        ; // minBagSizeToSearch;
        this.maxMetricValueToIndex = BlockInfo.getMaximumSimilarityThreshold(
                maxBagSizeToSearch, SearchManager.th);
        this.subShards = new ArrayList<Shard>();
        if (forWriting) {
            logger.debug("setinverted index");
            this.setInvertedIndexWriter();
            logger.debug("set forward index");
            this.setForwardIndexWriter();
        }
        this.size =0;
        logger.info("shard " + this + " created");
    }

    public int getId() {
        return id;
    }

    public int getMinMetricValue() {
        return minMetricValue;
    }

    public int getMaxMetricValue() {
        return maxMetricValue;
    }

    public int getMinMetricValueToIndex() {
        return minMetricValueToIndex;
    }

    public void setMinMetricValueToIndex(int minMetricValueToIndex) {
        this.minMetricValueToIndex = minMetricValueToIndex;
    }

    public int getMaxMetricValueToIndex() {
        return maxMetricValueToIndex;
    }

    public void setMaxMetricValueToIndex(int maxMetricValueToIndex) {
        this.maxMetricValueToIndex = maxMetricValueToIndex;
    }

    public IndexWriter getInvertedIndexWriter() {
        return invertedIndexWriter;
    }

    public void setInvertedIndexWriter() {
        WhitespaceAnalyzer whitespaceAnalyzer = new WhitespaceAnalyzer(
                Version.LUCENE_46);
        IndexWriterConfig indexWriterConfig = new IndexWriterConfig(
                Version.LUCENE_46, whitespaceAnalyzer);
        indexWriterConfig.setRAMBufferSizeMB(SearchManager.ramBufferSizeMB);
        indexWriterConfig.setOpenMode(OpenMode.CREATE);
        // index
        TieredMergePolicy mergePolicy = (TieredMergePolicy) indexWriterConfig
                .getMergePolicy();

        mergePolicy.setNoCFSRatio(0);// what was this for?
        mergePolicy.setMaxCFSSegmentSizeMB(0); // what was this for?

        try {
            FSDirectory dir = FSDirectory.open(new File(SearchManager.ROOT_DIR
                    + SearchManager.NODE_PREFIX + "/index/shards/" + this.indexPath));
            if (SearchManager.invertedIndexDirectoriesOfShard.containsKey(this.indexPath)) {
                List<FSDirectory> dirs = SearchManager.invertedIndexDirectoriesOfShard
                        .get(id);
                dirs.add(dir);
            } else {
                List<FSDirectory> dirs = new ArrayList<FSDirectory>();
                dirs.add(dir);
                SearchManager.invertedIndexDirectoriesOfShard.put(this.indexPath, dirs);
            }
            this.invertedIndexWriter = new IndexWriter(dir, indexWriterConfig);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public IndexWriter getForwardIndexWriter() {
        return forwardIndexWriter;
    }

    public void setForwardIndexWriter() {
        KeywordAnalyzer keywordAnalyzer = new KeywordAnalyzer();
        IndexWriterConfig fwdIndexWriterConfig = new IndexWriterConfig(
                Version.LUCENE_46, keywordAnalyzer);
        fwdIndexWriterConfig.setRAMBufferSizeMB(SearchManager.ramBufferSizeMB);
        fwdIndexWriterConfig.setOpenMode(OpenMode.CREATE);
        TieredMergePolicy mergePolicy = (TieredMergePolicy) fwdIndexWriterConfig
                .getMergePolicy();

        mergePolicy.setNoCFSRatio(0);// what was this for?
        mergePolicy.setMaxCFSSegmentSizeMB(0); // what was this for?
        try {
            FSDirectory dir = FSDirectory.open(new File(SearchManager.ROOT_DIR
                    + SearchManager.NODE_PREFIX + "/fwdindex/shards/" + this.indexPath));
            if (SearchManager.forwardIndexDirectoriesOfShard.containsKey(this.indexPath)) {
                List<FSDirectory> dirs = SearchManager.forwardIndexDirectoriesOfShard
                        .get(this.indexPath);
                dirs.add(dir);
            } else {
                List<FSDirectory> dirs = new ArrayList<FSDirectory>();
                dirs.add(dir);
                SearchManager.forwardIndexDirectoriesOfShard.put(this.indexPath, dirs);
            }
            this.forwardIndexWriter = new IndexWriter(dir,
                    fwdIndexWriterConfig);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void closeInvertedIndexWriter() {
        try {
            if (this.subShards.size()>0){
                for (Shard shard : this.subShards){
                    shard.closeInvertedIndexWriter();
                }
            }else{
                this.invertedIndexWriter.close();
                logger.info("Shard size: "+ this.size+", Shard Path: "+this.indexPath);
            }
        } catch (IOException e) {
            logger.error(e.getMessage());
        }
    }

    public void closeForwardIndexWriter() {
        try {
            if (this.subShards.size()>0){
                for (Shard shard : this.subShards){
                    shard.closeForwardIndexWriter();
                }
            }else{
                this.forwardIndexWriter.close();
            }
        } catch (IOException e) {
            logger.error(e.getMessage());
        }
    }

    /* (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "Shard [id=" + id + ", minSizeToSearch=" + minMetricValue + ", maxSizeToSearch="
                + maxMetricValue + ", minBagSizeToIndex=" + minMetricValueToIndex
                + ", maxBagSizeToIndex=" + maxMetricValueToIndex + ", indexPath="
                + indexPath +", subshardsSize="+subShards.size() +"]";
    }

}