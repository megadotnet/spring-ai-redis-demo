package com.redis.demo.spring.ai;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import com.redis.demo.spring.ai.model.RetrievalResult;
import com.redis.demo.spring.ai.service.RagService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class RagController {

	private final RagService ragService;

	public RagController(RagService ragService) {
		this.ragService = ragService;
	}

	@PostMapping("/chat/startChat")
	@ResponseBody
	public Message startChat() {
		return Message.of(UUID.randomUUID().toString());
	}

	//tag::chatMessage[]
	@PostMapping("/chat/{chatId}")
// 1. 移除 @ResponseBody (ResponseEntity 包含了响应体逻辑)
	public CompletableFuture<ResponseEntity<Message>> chatMessage(
			@PathVariable("chatId") String chatId,
			@RequestBody Prompt prompt) { // 假设 Prompt 是您的 DTO

		// 2. 开启异步任务，不占用 Tomcat 线程
		return CompletableFuture.supplyAsync(() -> {
					// 这里执行耗时操作 (25s+)
					// 注意：请确保 ragService 内部调用的 Client 超时设置已大于 60s
					// 假设 prompt.getContents() 获取文本，请根据实际对象调整
					return ragService.retrieve(prompt.getPrompt());
				})
				// 3. 设置 Java 层面的超时 (JDK 9+ 支持 orTimeout)
				// 建议设置为 60秒，给 Ollama 留足余地
				.orTimeout(60, TimeUnit.SECONDS)

				// 4. 成功时的处理
				.thenApply(generation -> {
					Message message = Message.of(generation.getOutput().getText());
					return ResponseEntity.ok(message);
				})

				// 5. 异常或超时时的处理
				.exceptionally(ex -> {
					// 区分是超时还是其他错误
					String errorMsg = "处理请求失败";
					if (ex instanceof java.util.concurrent.TimeoutException) {
						errorMsg = "LLM 服务响应超时，请稍后重试";
					} else {
						errorMsg = "错误: " + ex.getMessage();
					}
					return ResponseEntity.status(500).body(Message.of(errorMsg));
				});
	}
	//end::chatMessage[]

	@PostMapping("/documents/upload")
	@ResponseBody
	public String uploadDocument(String doc) {
		return "Document upload not supported";
	}


	@PostMapping("/retrieve")
	@ResponseBody
	public List<RetrievalResult> retrieve(@RequestBody Map<String, String> payload) {
		 return ragService.retrieveForTesting(payload);
	}

	public static class Message {

		private String message;

		public String getMessage() {
			return message;
		}

		public void setMessage(String message) {
			this.message = message;
		}

		public static Message of(String message) {
			Message response = new Message();
			response.setMessage(message);
			return response;
		}

	}



	public static class Prompt {

		private String prompt;

		public String getPrompt() {
			return prompt;
		}

		public void setPrompt(String prompt) {
			this.prompt = prompt;
		}

	}

}