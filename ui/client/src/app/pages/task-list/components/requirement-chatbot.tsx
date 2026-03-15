import React from "react";

import Chatbot, {
  ChatbotDisplayMode,
} from "@patternfly/chatbot/dist/dynamic/Chatbot";
import ChatbotContent from "@patternfly/chatbot/dist/dynamic/ChatbotContent";
import ChatbotFooter from "@patternfly/chatbot/dist/dynamic/ChatbotFooter";
import ChatbotHeader, {
  ChatbotHeaderActions,
  ChatbotHeaderSelectorDropdown,
} from "@patternfly/chatbot/dist/dynamic/ChatbotHeader";
import ChatbotWelcomePrompt from "@patternfly/chatbot/dist/dynamic/ChatbotWelcomePrompt";
import Message from "@patternfly/chatbot/dist/dynamic/Message";
import MessageBar from "@patternfly/chatbot/dist/dynamic/MessageBar";
import MessageBox from "@patternfly/chatbot/dist/dynamic/MessageBox";
import { DropdownItem, DropdownList } from "@patternfly/react-core";

import { sendChatMessage } from "@app/api/task-api";

interface ChatMessage {
  role: "user" | "bot";
  content: string;
}

interface RequirementChatbotProps {
  taskId: number;
}

export const RequirementChatbot: React.FC<RequirementChatbotProps> = ({
  taskId,
}) => {
  const [messages, setMessages] = React.useState<ChatMessage[]>([]);
  const [isLoading, setIsLoading] = React.useState(false);

  const handleSendMessage = React.useCallback(
    async (messageText: string) => {
      const userMessage: ChatMessage = { role: "user", content: messageText };
      setMessages((prev) => [...prev, userMessage]);
      setIsLoading(true);

      const botMessage: ChatMessage = { role: "bot", content: "" };
      setMessages((prev) => [...prev, botMessage]);

      try {
        for await (const chunk of sendChatMessage(taskId, messageText)) {
          setMessages((prev) => {
            const updated = [...prev];
            const last = updated[updated.length - 1];
            if (last.role === "bot") {
              updated[updated.length - 1] = {
                ...last,
                content: last.content + chunk,
              };
            }
            return updated;
          });
        }
      } catch {
        setMessages((prev) => {
          const updated = [...prev];
          const last = updated[updated.length - 1];
          if (last.role === "bot" && last.content === "") {
            updated[updated.length - 1] = {
              ...last,
              content: "Sorry, an error occurred. Please try again.",
            };
          }
          return updated;
        });
      } finally {
        setIsLoading(false);
      }
    },
    [taskId],
  );

  const handlePromptClick = React.useCallback(
    (message: string) => {
      handleSendMessage(message);
    },
    [handleSendMessage],
  );

  return (
    <Chatbot displayMode={ChatbotDisplayMode.embedded}>
      <ChatbotHeader>
        <ChatbotHeaderActions>
          <ChatbotHeaderSelectorDropdown
            value={"Granite 7B"}
            onSelect={() => {}}
          >
            <DropdownList>
              <DropdownItem value="Granite 7B" key="granite">
                Granite 7B
              </DropdownItem>
              <DropdownItem value="Llama 3.0" key="llama">
                Llama 3.0
              </DropdownItem>
              <DropdownItem value="Mistral 3B" key="mistral">
                Mistral 3B
              </DropdownItem>
            </DropdownList>
          </ChatbotHeaderSelectorDropdown>
        </ChatbotHeaderActions>
      </ChatbotHeader>
      <ChatbotContent>
        <MessageBox>
          {messages.length === 0 ? (
            <ChatbotWelcomePrompt
              title="Requirement Assistant"
              description="I can help refine requirements and answer questions about this task."
              prompts={[
                {
                  title: "Enrich requirement",
                  message:
                    "Enrich the requirement definition with more details and structure.",
                  onClick: () =>
                    handlePromptClick(
                      "Enrich the requirement definition with more details and structure.",
                    ),
                },
                {
                  title: "Acceptance criteria",
                  message:
                    "Identify and list the acceptance criteria for this requirement.",
                  onClick: () =>
                    handlePromptClick(
                      "Identify and list the acceptance criteria for this requirement.",
                    ),
                },
              ]}
            />
          ) : (
            messages.map((msg, index) => (
              <Message
                // biome-ignore lint/suspicious/noArrayIndexKey: deterministic index
                key={`${index}`}
                role={msg.role}
                content={msg.content}
                name={msg.role === "user" ? "You" : "Assistant"}
              />
            ))
          )}
        </MessageBox>
      </ChatbotContent>
      <ChatbotFooter>
        <MessageBar
          onSendMessage={(message) => handleSendMessage(String(message))}
          hasAttachButton={false}
          isSendButtonDisabled={isLoading}
        />
      </ChatbotFooter>
    </Chatbot>
  );
};
