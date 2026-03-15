import type React from "react";

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
import MessageBar from "@patternfly/chatbot/dist/dynamic/MessageBar";
import MessageBox from "@patternfly/chatbot/dist/dynamic/MessageBox";
import { DropdownItem, DropdownList } from "@patternfly/react-core";

export const RequirementChatbot: React.FC = () => {
  return (
    <Chatbot
      displayMode={ChatbotDisplayMode.embedded}
      // style={{ height: "100%" }}
    >
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
          <ChatbotWelcomePrompt
            title="Hi, ChatBot User!"
            description="How can I help you today?"
            prompts={[
              {
                title: "Set up account",
                message:
                  "Choose the necessary settings and preferences for your account.",
              },
              {
                title: "Troubleshoot issue",
                message:
                  "Find documentation and instructions to resolve your issue.",
              },
            ]}
          />
          more messages
        </MessageBox>
      </ChatbotContent>
      <ChatbotFooter>
        <MessageBar onSendMessage={() => {}} />
      </ChatbotFooter>
    </Chatbot>
  );
};
