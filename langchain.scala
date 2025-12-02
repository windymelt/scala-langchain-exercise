//> using scala 3.7
//> using platform jvm
//> using dep dev.langchain4j:langchain4j:1.9.1
//> using dep dev.langchain4j:langchain4j-open-ai:1.9.1
//> using dep com.softwaremill.ox::core:1.0.2
//> using option -Wunused:imports
//> using scalafix.dep ch.epfl.scala::scalafix-core:0.13.0

import dev.langchain4j.model.chat.response.ChatResponse
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler
import dev.langchain4j.model.openai.OpenAiStreamingChatModel
import ox.*
import ox.channels.Channel
import ox.channels.ChannelClosed.Done

object Main extends OxApp.Simple:
  val apikeyEnv = "OPENAI_API_KEY"
  val baseUrl = "https://api.ai.sakura.ad.jp/v1/"
  val modelName = "llm-jp-3.1-8x13b-instruct4"

  def run(using Ox): Unit = {
    val OPENAI_API_KEY = sys.env.get(apikeyEnv)
    if OPENAI_API_KEY.isEmpty then
      throw Exception(s"Enviroment varible $$$apikeyEnv is not defined")

    val model =
      OpenAiStreamingChatModel
        .builder()
        .apiKey(OPENAI_API_KEY.get)
        .baseUrl(baseUrl)
        .modelName(modelName)
        .build()

    val c = Channel.rendezvous[String]
    val srh = new StreamingChatResponseHandler {
      override def onPartialResponse(token: String): Unit = c.send(token)
      def onCompleteResponse(response: ChatResponse): Unit = c.done()
      def onError(error: Throwable): Unit = c.error(error)
    }

    supervised:
      fork:
        model.chat("LangChainとは何ですか？", srh)

      forkUser:
        repeatUntil:
          c.receiveOrClosed() match
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
