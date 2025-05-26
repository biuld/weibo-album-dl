package com.github.biuld.widget

import terminus.*
import terminus.effect.{Reader, Writer, Color}

object Input {
  // State to track cursor position and input buffer
  case class State(
      buffer: String = "",
      cursorPos: Int = 0,
      prompt: String,
      history: List[String] = List.empty,
      historyIndex: Int = -1,  // -1 means current input, 0 means most recent history
      shouldExit: Boolean = false  // New field to track exit request
  )

  // Key handlers
  private object KeyHandlers {
    def handleSpecialKey(state: State, key: Key): Program[State] = {
      key match {
        case Key.backspace =>
          if state.cursorPos > 0 then {
            val newBuffer = state.buffer.substring(0, state.cursorPos - 1) +
              state.buffer.substring(state.cursorPos)
            
            if state.cursorPos == state.buffer.length then {
              // If cursor is at the end, just delete the last character
              Terminal.write("\b \b") // Backspace, space, backspace
            } else {
              // If cursor is in the middle, delete and shift remaining characters
              Terminal.write("\b") // Move back
              Terminal.write("\u001b[1P") // Delete character
            }
            
            Terminal.flush()
            state.copy(buffer = newBuffer, cursorPos = state.cursorPos - 1)
          } else state
        case Key.delete =>
          if state.cursorPos < state.buffer.length then {
            val newBuffer = state.buffer.substring(0, state.cursorPos) +
              state.buffer.substring(state.cursorPos + 1)
            Terminal.write("\u001b[1P") // Delete character
            Terminal.flush()
            state.copy(buffer = newBuffer)
          } else state
        case _ => state
      }
    }

    def handleCtrlKey(state: State, key: Key): Program[State] = {
      key.code match {
        case KeyCode.Character('a') => // Ctrl+A: Move to start of line
          Terminal.write(s"\r\u001b[${state.prompt.length}C") // Move to start of line
          Terminal.flush()
          state.copy(cursorPos = 0)
          
        case KeyCode.Character('e') => // Ctrl+E: Move to end of line
          Terminal.write(s"\u001b[${state.buffer.length - state.cursorPos}C") // Move to end
          Terminal.flush()
          state.copy(cursorPos = state.buffer.length)
          
        case KeyCode.Character('u') => // Ctrl+U: Delete everything before cursor
          if state.cursorPos > 0 then {
            Terminal.write(s"\r\u001b[${state.prompt.length}C") // Move to start of input
            Terminal.write("\u001b[K") // Clear to end of line
            Terminal.write(state.buffer.substring(state.cursorPos)) // Rewrite remaining text
            Terminal.write(s"\u001b[${state.buffer.length - state.cursorPos}D") // Move cursor back
            Terminal.flush()
            state.copy(buffer = state.buffer.substring(state.cursorPos), cursorPos = 0)
          } else state
          
        case KeyCode.Character('k') => // Ctrl+K: Delete everything after cursor
          if state.cursorPos < state.buffer.length then {
            Terminal.write("\u001b[K") // Clear to end of line
            Terminal.flush()
            state.copy(buffer = state.buffer.substring(0, state.cursorPos))
          } else state
          
        case KeyCode.Character('l') => // Ctrl+L: Clear screen
          Terminal.write("\u001b[2J\u001b[H") // Clear screen and move to top
          Terminal.foreground.cyan {
            Terminal.format.bold {
              Terminal.write(state.prompt)
            }
          }
          Terminal.write(state.buffer)
          Terminal.write(s"\u001b[${state.buffer.length - state.cursorPos}D") // Move cursor back
          Terminal.flush()
          state

        case KeyCode.Character('d') => // Ctrl+D: Exit if buffer is empty
          if state.buffer.isEmpty then {
            state.copy(shouldExit = true)
          } else state
        case _ => state
      }
    }

    def handleArrowKey(state: State, key: Key): Program[State] = {
      key match {
        case Key.right =>
          if state.cursorPos < state.buffer.length then {
            Terminal.write("\u001b[C") // Move right
            Terminal.flush()
            state.copy(cursorPos = state.cursorPos + 1)
          } else state
        case Key.left =>
          if state.cursorPos > 0 then {
            Terminal.write("\u001b[D") // Move left
            Terminal.flush()
            state.copy(cursorPos = state.cursorPos - 1)
          } else state
        case Key.up =>
          if state.historyIndex < state.history.length - 1 then {
            val newIndex = state.historyIndex + 1
            val newBuffer = state.history(newIndex)
            // Clear current line and show history item
            Terminal.write("\r\u001b[K") // Move to start of line and clear
            Terminal.write(state.prompt + newBuffer)
            Terminal.flush()
            state.copy(
              buffer = newBuffer,
              cursorPos = newBuffer.length,
              historyIndex = newIndex
            )
          } else state
        case Key.down =>
          if state.historyIndex > -1 then {
            val newIndex = state.historyIndex - 1
            val newBuffer = if newIndex == -1 then "" else state.history(newIndex)
            // Clear current line and show history item or empty buffer
            Terminal.write("\r\u001b[K")// Move to start of line and clear
            Terminal.write(state.prompt + newBuffer)
            Terminal.flush()
            state.copy(
              buffer = newBuffer,
              cursorPos = newBuffer.length,
              historyIndex = newIndex
            )
          } else state
        case _ => state
      }
    }
  }

  def readLine(prompt: String, history: List[String]): Program[Option[String]] = {
    def loop(state: State): Program[Option[String]] = {
      if state.shouldExit then None
      else
        Terminal.readKey() match {
          case Key.enter =>
            Terminal.write("\n")
            Terminal.flush()
            Some(state.buffer)

          case key: Key if key.modifiers == KeyModifier.Control =>
            val newState = key.code match {
              case KeyCode.Character(_) => KeyHandlers.handleCtrlKey(state, key)
              case _ => KeyHandlers.handleSpecialKey(state, key)
            }
            loop(newState)

          case key: Key
              if key.code == KeyCode.Left || key.code == KeyCode.Right ||
                 key.code == KeyCode.Up || key.code == KeyCode.Down =>
            val newState = KeyHandlers.handleArrowKey(state, key)
            loop(newState)

          case Key(KeyModifier.None, KeyCode.Character(c)) =>
            val newBuffer = state.buffer.substring(0, state.cursorPos) + c +
              state.buffer.substring(state.cursorPos)
            // Only write the new character and move cursor
            Terminal.write(c.toString)
            Terminal.flush()
            loop(
              state.copy(
                buffer = newBuffer,
                cursorPos = state.cursorPos + 1,
                historyIndex = -1  // Reset history index when typing
              )
            )

          case key: Key if key.code == KeyCode.Backspace || key.code == KeyCode.Delete =>
            val newState = KeyHandlers.handleSpecialKey(state, Key.backspace)
            loop(newState.copy(historyIndex = -1))  // Reset history index when deleting

          case _ => loop(state)
        }
    }

    Terminal.raw {      
      // Disable echo and canonical mode
      Terminal.write("\u001b[?25l") // Hide cursor
      Terminal.write("\u001b[?12l") // Disable local echo
      Terminal.write("\u001b[?1l")  // Disable cursor key mode
      
      Terminal.foreground.cyan {
        Terminal.format.bold {
          Terminal.write(prompt)
        }
      }
      
      // Show cursor after prompt
      Terminal.write("\u001b[?25h") // Show cursor
      Terminal.flush()

      loop(State(prompt = prompt, history = history))
    }
  }
}
