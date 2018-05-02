package come.prince.spark.bayes

import java.io.StringReader

import org.apache.log4j.{Level, Logger}
import org.apache.lucene.analysis.TokenStream
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute
import org.apache.spark.sql.SparkSession
import org.wltea.analyzer.lucene.IKAnalyzer

/**
  * 中文分词以及词频统计
  * Created by princeping on 2018/4/28.
  */
object IKAnalyzerDemo {

  Logger.getLogger("org.apache.spark").setLevel(Level.WARN)

  def main(args: Array[String]): Unit = {

    val spark = SparkSession.builder().master("local").getOrCreate()
    val input = spark.sparkContext.textFile("E:\\workspace\\spark\\src\\main\\resources\\input.txt")

    val result = input
      .filter(_.length != 0)
      .map(line => {
        val analyzer = new IKAnalyzer(true)
        val reader = new StringReader(line.toString)
        val ts:TokenStream = analyzer.tokenStream("", reader)
        val term:CharTermAttribute = ts.getAttribute(classOf[CharTermAttribute])

        var words: String = ""
        while (ts.incrementToken()) {
          words += (term.toString + "\t")
        }
        analyzer.close()
        reader.close()
        words
      })

    val wordCount = result
      .flatMap(_.split("\t"))
      .filter(_ != "")
      .map((_, 1))
      .reduceByKey(_ + _)
    wordCount.foreach(println)
  }
}
