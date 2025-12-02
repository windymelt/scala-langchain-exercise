//> using scala 3.7
//> using platform jvm
//> using dep dev.langchain4j:langchain4j:1.9.1
//> using dep dev.langchain4j:langchain4j-open-ai:1.9.1
//> using dep com.softwaremill.ox::core:1.0.2
//> using option -Wunused:imports
//> using scalafix.dep ch.epfl.scala::scalafix-core:0.13.0
//> using dep dev.langchain4j:langchain4j-core:1.9.1

import dev.langchain4j.agent.tool.Tool
import dev.langchain4j.memory.chat.MessageWindowChatMemory
import dev.langchain4j.model.chat.response.ChatResponse
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler
import dev.langchain4j.model.openai.OpenAiStreamingChatModel
import dev.langchain4j.service.AiServices
import dev.langchain4j.service.TokenStream
import dev.langchain4j.service.UserMessage
import ox.*
import ox.channels.Channel
import ox.channels.ChannelClosed.Done

object Main extends OxApp.Simple {
  val apikeyEnv = "OPENAI_API_KEY"
  val baseUrl = "https://api.ai.sakura.ad.jp/v1/"
  val modelName = "gpt-oss-120b"

  def run(using Ox): Unit = {
    val OPENAI_API_KEY = sys.env.get(apikeyEnv)
    if (OPENAI_API_KEY.isEmpty) {
      throw Exception(s"Enviroment varible $$$apikeyEnv is not defined")
    }

    val model =
      OpenAiStreamingChatModel
        .builder()
        .apiKey(OPENAI_API_KEY.get)
        .baseUrl(baseUrl)
        .modelName(modelName)
        .build()

    val reverser = new StringReverser()
    val assistant = AiServices
      .builder(classOf[Assistant])
      .streamingChatModel(model)
      .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
      .tools(reverser)
      .build()

    val c = Channel.rendezvous[String]

    supervised {
      fork {
        val prompt =
          "以下の文章を反転させてください。 'The quick brown fox jumps over the lazy dog!'"

        assistant
          .chat(prompt)
          .onError(e => c.error(e))
          .onPartialResponse(r => c.send(r))
          .onCompleteResponse(_ => c.done())
          .start()
      }

      forkUser {
        repeatUntil {
          c.receiveOrClosed() match {
            case ox.channels.ChannelClosed.Error(reason) =>
              println("Error occurred")
              println(reason.getMessage())
              true
            case Done =>
              println("*")
              true
            case s: String =>
              print(s)
              false
          }
        }
      }
    }
  }
}

class StringReverser {
  @Tool(Array("Reverses a given string"))
  def reverseString(input: String): String = {
    input.reverse.toUpperCase()
  }
}

trait Assistant {
  @UserMessage(Array("{{it}}"))
  def chat(userMessage: String): TokenStream
}
