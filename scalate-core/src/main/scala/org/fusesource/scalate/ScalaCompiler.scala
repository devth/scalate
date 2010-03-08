/*
 * Copyright (c) 2009 Matthew Hildebrand <matt.hildebrand@gmail.com>
 *
 * Permission to use, copy, modify, and/or distribute this software for any
 * purpose with or without fee is hereby granted, provided that the above
 * copyright notice and this permission notice appear in all copies.
 *
 * THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
 * WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR
 * ANY SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
 * WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN
 * ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF
 * OR IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.
 */


package org.fusesource.scalate.ssp

import org.fusesource.scalate._
import org.fusesource.scalate.util.ClassPathBuilder
import org.fusesource.scalate.util.Logging
import org.fusesource.scalate.util.Sequences.removeDuplicates
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import scala.tools.nsc.Global
import scala.tools.nsc.Settings
import scala.tools.nsc.reporters.ConsoleReporter

class ScalaCompiler(bytecodeDirectory: File, classpath: String, combineClasspath: Boolean = false) extends Logging {

  val settings = generateSettings(bytecodeDirectory, classpath, combineClasspath)
  val compiler = new Global(settings, null)

  def compile(file:File): Unit = {
    
    synchronized {
      val messageCollector = new StringWriter
      val messageCollectorWrapper = new PrintWriter(messageCollector)
      val reporter = new ConsoleReporter(settings, Console.in, messageCollectorWrapper)
      compiler.reporter = reporter

      // Attempt compilation
      (new compiler.Run).compile(List(file.getCanonicalPath))

      // Bail out if compilation failed
      if (reporter.hasErrors) {
        reporter.printSummary
        messageCollectorWrapper.close
        throw new TemplateException("Compilation failed:\n" +messageCollector)
      }
    }
  }

  private def error(message: String): Unit = throw new TemplateException("Compilation failed:\n" + message)

  private def generateSettings(bytecodeDirectory: File, classpath: String, combineClasspath: Boolean): Settings = {
    bytecodeDirectory.mkdirs

    val pathSeparator = File.pathSeparator

    var useCP = if (classpath != null && !combineClasspath) {
      classpath
    } else {
      (new ClassPathBuilder).addPathFromContextClassLoader()
                            .addPathFrom(classOf[Product])
                            .addPathFrom(classOf[Global])
                            .addPathFrom(getClass)
                            .addPathFromSystemClassLoader()
                            .addEntry(classpath)
                            .addJavaPath()
                            .classPath
    }

    fine("using classpath: " + useCP)

    val settings = new Settings(error)
    settings.classpath.value = useCP
    settings.outdir.value = bytecodeDirectory.toString
    settings.deprecation.value = true
    settings.unchecked.value = true
    // TODO not sure if these changes make much difference?
    //settings.make.value = "transitivenocp"
    settings
  }
}
