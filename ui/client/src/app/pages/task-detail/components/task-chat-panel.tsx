import React from "react";

import Chatbot, {
  ChatbotDisplayMode,
} from "@patternfly/chatbot/dist/dynamic/Chatbot";
import ChatbotContent from "@patternfly/chatbot/dist/dynamic/ChatbotContent";
import ChatbotFooter from "@patternfly/chatbot/dist/dynamic/ChatbotFooter";
import ChatbotWelcomePrompt from "@patternfly/chatbot/dist/dynamic/ChatbotWelcomePrompt";
import Message from "@patternfly/chatbot/dist/dynamic/Message";
import MessageBar from "@patternfly/chatbot/dist/dynamic/MessageBar";
import MessageBox from "@patternfly/chatbot/dist/dynamic/MessageBox";

import { sendChatMessage } from "@app/api/task-api";
import botAvatar from "@app/images/bot_avatar.jpg";
import userAvatar from "@app/images/user_avatar.svg";

interface ChatMessage {
  role: "user" | "bot";
  content: string;
}

interface TaskChatPanelProps {
  taskId: number;
  hasWorkspace: boolean;
}

export const TaskChatPanel: React.FC<TaskChatPanelProps> = ({
  taskId,
  hasWorkspace,
}) => {
  const [messages, setMessages] = React.useState<ChatMessage[]>([]);
  const [isLoading, setIsLoading] = React.useState(false);

  const handleSendMessage = React.useCallback(
    async (messageText: string) => {
      const userMessage: ChatMessage = { role: "user", content: messageText };
      setMessages((prev) => [...prev, userMessage]);
      setIsLoading(true);

      let hasStarted = false;

      try {
        for await (const chunk of sendChatMessage(taskId, messageText)) {
          if (!hasStarted) {
            hasStarted = true;
            setMessages((prev) => [...prev, { role: "bot", content: chunk }]);
          } else {
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
        }
        if (!hasStarted) {
          setMessages((prev) => [
            ...prev,
            { role: "bot", content: "No response received." },
          ]);
        }
      } catch {
        setMessages((prev) => [
          ...prev,
          ...(!hasStarted
            ? [
                {
                  role: "bot" as const,
                  content: "Sorry, an error occurred. Please try again.",
                },
              ]
            : []),
        ]);
      } finally {
        setIsLoading(false);
      }
    },
    [taskId],
  );

  const welcomePrompts = React.useMemo(() => {
    const prompts = [
      {
        title: "Enrich requirement",
        message: "Enrich the requirement with more details and structure.",
        onClick: () =>
          handleSendMessage(
            "Enrich the requirement with more details and structure.",
          ),
      },
      {
        title: "Generate a plan",
        message:
          "Generate an implementation plan based on the current requirement.",
        onClick: () =>
          handleSendMessage(
            "Generate an implementation plan based on the current requirement.",
          ),
      },
    ];

    if (hasWorkspace) {
      prompts.push({
        title: "Analyze the codebase",
        message: "What are the main components and structure of this project?",
        onClick: () =>
          handleSendMessage(
            "What are the main components and structure of this project?",
          ),
      });
    }

    return prompts;
  }, [hasWorkspace, handleSendMessage]);

  return (
    <Chatbot displayMode={ChatbotDisplayMode.embedded}>
      <ChatbotContent>
        <MessageBox enableSmartScroll>
          {messages.length === 0 ? (
            <ChatbotWelcomePrompt
              title="Task Assistant"
              description={
                hasWorkspace
                  ? "I can help you understand the codebase, generate plans, execute changes, and create pull requests."
                  : "I can help refine requirements and generate plans. Provision a workspace to enable code changes."
              }
              prompts={welcomePrompts}
            />
          ) : (
            <>
              {messages.map((msg, index) => (
                <Message
                  // biome-ignore lint/suspicious/noArrayIndexKey: deterministic index
                  key={`${index}`}
                  role={msg.role}
                  content={msg.content}
                  name={msg.role === "user" ? "You" : "Assistant"}
                  avatar={msg.role === "user" ? userAvatar : botAvatar}
                />
              ))}
              {isLoading &&
                (messages.length === 0 ||
                  messages[messages.length - 1].role === "user") && (
                  // biome-ignore lint/a11y/useValidAriaRole: role="bot" is a PatternFly Chatbot convention
                  <Message
                    role="bot"
                    name="Assistant"
                    avatar={botAvatar}
                    isLoading
                  />
                )}
            </>
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
