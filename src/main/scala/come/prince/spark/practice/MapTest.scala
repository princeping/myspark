package come.prince.spark.practice

import org.apache.log4j.{Level, Logger}

/**
  * Created by princeping on 2018/4/12.
  */
object MapTest {

  Logger.getLogger("org").setLevel(Level.WARN)

  def main(args: Array[String]): Unit = {

    //不可变集合，immutable
    val studentInfo = Map("john" -> 21, "mike" -> 22, "lucy" -> 20)
    //val studentInfo = scala.collection.immutable.Map("john" -> 21, "mike" -> 22, "lucy" -> 20)

    //3种遍历方法
    for (i <- studentInfo)
      println(i)

    studentInfo.foreach(x =>{
      val (k,v) = x
      println(k + ":" + v)
    })

    studentInfo.foreach(x => {
      println(x._1 + ":" + x._2)
    })


    /*============================================================================================*/


    //可变集合，mutable
    val studentInfoMutable = scala.collection.mutable.Map("john" -> 21, "mike" -> 22, "lucy" -> 20)
    //studentInfoMutable.clear()
    for (i <- studentInfoMutable)
      println(i)
  }
}
