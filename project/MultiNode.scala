package akka

import _root_.sbt._
import _root_.sbt.Keys._
import _root_.sbt.TaskKey
import sbtassembly.Plugin._
import AssemblyKeys._
import sbinary.DefaultProtocol.StringFormat
import Cache.seqFormat
import com.typesafe.multinodetest.Master

object MultiNode {

  import MultiNodeKeys._

  object MultiNodeKeys {
    val multiNodeMarker = SettingKey[String]("multi-node-marker")

    val multiNodeTests = TaskKey[Seq[TestDefinition]]("multi-node-tests")
    val multiNodeTestNames = TaskKey[Seq[String]]("multi-node-test-names")

    val multiNodeApps = TaskKey[Seq[String]]("multi-node-apps")
    val multiNodeAppNames = TaskKey[Seq[String]]("multi-node-app-names")

    val multiNodeTest = TaskKey[Unit]("multi-node-test")
    val multiNodeTestOnly = InputKey[Unit]("multi-node-test-only")

    val multiNodeTestJar = SettingKey[File]("multi-node-test-jar")
  }

  lazy val settings: Seq[Setting[_]] = multiNodeSettings

  def multiNodeSettings = assemblySettings ++ Seq(
    // right now we try to run single jvm tests only, but through the server/client
    multiNodeMarker := "MultiJvm",
    multiNodeTests <<= ((definedTests in Test), multiNodeMarker) map
      { (td: Seq[TestDefinition], m: String) => td.filter(! _.name.contains(m)) },
    multiNodeTestNames <<= multiNodeTests map(_.map(_.name)) storeAs multiNodeTestNames triggeredBy (compile in Test),
    multiNodeApps <<= ((discoveredMainClasses in Compile), multiNodeMarker) map
      { (mc: Seq[String], m: String) => mc.filter(! _.contains(m)) },
    multiNodeAppNames <<= multiNodeApps map(x => x) storeAs multiNodeAppNames triggeredBy (compile in Compile),
    multiNodeTest <<= multiNodeTestTask,
    multiNodeTestOnly <<= multiNodeTestOnlyTask,

      // here follows the assembly parts of the config
    // don't run the tests when creating the assembly
    test in assembly := {},
    // we want everyhting including the tests and test frameworks
    fullClasspath in assembly <<= fullClasspath in Test,
    // the first class wins just like a classpath
    // just concatenate conflicting text files
    mergeStrategy in assembly <<= (mergeStrategy in assembly) {
      (old) => {
        case n if n.endsWith(".class") => MergeStrategy.first
        case n if n.endsWith(".txt") => MergeStrategy.concat
        case n if n.endsWith("NOTICE") => MergeStrategy.concat
        case n => old(n)
      }
    },
    multiNodeTestJar <<= (outputPath in assembly)
  )

  def multiNodeTestTask = (multiNodeTests, streams) map {
    (tests, s) => {
      if (tests.isEmpty) {
        s.log.info("No tests to run.")
      }
      else {
        tests.foreach {
          case (test) => s.log.info("This is a test " + test.name + " " + test.fingerprint)
        }
      }
    }
  }

  def multiNodeTestOnlyTask = InputTask(loadForParser(multiNodeTestNames)((s, i) => Defaults.testOnlyParser(s, i getOrElse Nil))) { result =>
    (multiNodeTests, streams, multiNodeTestJar, result) map {
      case (seq, s, jname, (tests, extraOptions)) =>
        tests foreach { name =>
          if (!seq.exists(_.name.compareTo(name) == 0)) {
            s.log.info("No tests to run. '" + name + "' not in " + seq)
          }
          else {
            s.log.info("This is da test " + name + " with options " + extraOptions)
            val master = new Master()
            master.runTestOnSlave(1729, List(jname.getAbsolutePath), "org.scalatest.tools.Runner", "run", List("-p", ".", "-o", "-s", name))
            master.stopSlave(1729)
            master.stop()
          }
        }
    }
  }

}
