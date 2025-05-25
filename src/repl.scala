import terminus.*
import terminus.effect.{Reader, Writer, Color}
import scala.util.{Try, Success, Failure}
import util.log
import util.scheduler
import api.weibo.Weibo
import java.io.FileNotFoundException
import java.util.concurrent.TimeUnit
import widget.InputLine

object Repl {
  // Command ADT
  enum Command:
    case Help
    case Exit
    case Walk(dir: String)
    case Download(uid: String, dir: String)
    case Unknown(name: String)

  // Command execution result
  enum CommandResult:
    case Continue
    case Exit

  // Parse input into Command
  private def parseCommand(input: String): Command = {
    val trimmed = input.trim
    if trimmed.isEmpty then Command.Unknown("")
    else
      val parts = trimmed.split("\\s+")
      parts(0).toLowerCase match
        case "help"    => Command.Help
        case "exit"    => Command.Exit
        case "walk" =>
          if parts.length > 1 then Command.Walk(parts(1))
          else Command.Unknown("walk")
        case "download" =>
          if parts.length > 2 then Command.Download(parts(1), parts(2))
          else Command.Unknown("download")
        case cmd => Command.Unknown(cmd)
  }

  // Execute command and return result
  private def executeCommand(cmd: Command): Program[CommandResult] = cmd match
    case Command.Help =>
      Terminal.foreground.cyan {
        Terminal.write("""
          |Available commands:
          |  help                    - Show this help message
          |  exit                    - Exit the program
          |  walk <dir>              - Walk through uids inside directory
          |  download <uid> <dir>    - Download all images of uid into directory
          |""".stripMargin)
        Terminal.flush()
      }
      CommandResult.Continue

    case Command.Exit =>
      Terminal.foreground.yellow {
        Terminal.write("Goodbye!\n")
        Terminal.flush()
      }
      CommandResult.Exit

    case Command.Walk(dir) =>
      Terminal.foreground.green {
        Terminal.write(s"Walking through directory: $dir\n")
        Terminal.flush()
      }
      try
        Weibo.walkDirectory(util.path(dir))
        Terminal.write("Finished walking through directory\n")
        Terminal.flush()
      catch
        case e: Exception =>
          Terminal.foreground.red {
            Terminal.write(s"Error: ${e.getMessage}\n")
            Terminal.flush()
          }
      CommandResult.Continue

    case Command.Download(uid, dir) =>
      Terminal.foreground.green {
        Terminal.write(
          s"Downloading images for uid $uid into directory: $dir\n"
        )
        Terminal.flush()
      }
      try
        val cnt = Weibo.downloadForUid(uid, util.path(dir))
        Terminal.write(s"Downloaded $cnt images\n")
        Terminal.flush()
      catch
        case e: Exception =>
          Terminal.foreground.red {
            Terminal.write(s"Error: ${e.getMessage}\n")
            Terminal.flush()
          }
      CommandResult.Continue

    case Command.Unknown(name) =>
      Terminal.foreground.red {
        Terminal.write(s"Unknown command: $name\n")
        Terminal.write("Type 'help' for available commands.\n")
        Terminal.flush()
      }
      CommandResult.Continue

  def run(): Unit = {
    // Use mutable list to store command history
    var history = List.empty[String]

    Terminal.run {
      // Show welcome message
      Terminal.foreground.green {
        Terminal.format.bold {
          Terminal.write("Welcome to weibo-album-dl REPL!\n")
          Terminal.write("Type 'help' for available commands.\n")
          Terminal.flush()
        }
      }

      // Main REPL loop
      def loop(): Program[Unit] = {
        InputLine.readLine("weibo-album-dl> ", history) match
          case Some(input) =>
            // Add non-empty commands to history
            if !input.trim.isEmpty then history = input :: history
            val cmd = parseCommand(input)
            executeCommand(cmd) match
              case CommandResult.Continue => loop()
              case CommandResult.Exit     => System.exit(0)
          case None =>
            Terminal.foreground.yellow {
              Terminal.write("\nGoodbye!\n")
              Terminal.flush()
            }
      }

      loop()
    }
  }
}
