import React from "react";

import {
  FormGroup,
  MenuToggle,
  Select,
  SelectList,
  SelectOption,
} from "@patternfly/react-core";
import { useForm, Controller } from "react-hook-form";
import { yupResolver } from "@hookform/resolvers/yup";
import * as yup from "yup";

import { useFetchGits } from "@app/queries/gits";
import { useFormChangeHandler } from "@app/hooks/useFormChangeHandler";

interface GitConfigurationValues {
  gitId: string;
}

export interface GitConfigurationState extends GitConfigurationValues {
  isValid: boolean;
}

const schema = yup.object({
  gitId: yup.string().defined().default(""),
});

interface GitConfigurationStepProps {
  initialState: GitConfigurationState;
  onStateChanged: (state: GitConfigurationState) => void;
}

export const GitConfigurationStep: React.FC<GitConfigurationStepProps> = ({
  initialState,
  onStateChanged,
}) => {
  const { data: gits } = useFetchGits();
  const [isOpen, setIsOpen] = React.useState(false);

  const form = useForm<GitConfigurationValues>({
    resolver: yupResolver(schema),
    mode: "all",
    defaultValues: { gitId: initialState.gitId },
  });

  useFormChangeHandler({ form, onStateChanged });

  const selectedGitId = form.watch("gitId");
  const selectedGit = gits?.find((g) => String(g.id) === selectedGitId);

  return (
    <FormGroup label="Git Repository" fieldId="git-select">
      <Controller
        control={form.control}
        name="gitId"
        render={({ field }) => (
          <Select
            id="git-select"
            isOpen={isOpen}
            selected={field.value || undefined}
            onSelect={(_event, value) => {
              field.onChange(String(value));
              setIsOpen(false);
            }}
            onOpenChange={setIsOpen}
            toggle={(toggleRef) => (
              <MenuToggle
                ref={toggleRef}
                onClick={() => setIsOpen(!isOpen)}
                isExpanded={isOpen}
                isFullWidth
              >
                {selectedGit ? selectedGit.url : "Select a repository"}
              </MenuToggle>
            )}
          >
            <SelectList>
              <SelectOption value="" description="No git repository">
                None
              </SelectOption>
              {gits?.map((git) => (
                <SelectOption
                  key={git.id}
                  value={String(git.id)}
                  description={[
                    git.forkUrl ? `Fork: ${git.forkUrl}` : null,
                    git.branch ? `Branch: ${git.branch}` : null,
                  ]
                    .filter(Boolean)
                    .join(" | ")}
                >
                  {git.url}
                </SelectOption>
              ))}
            </SelectList>
          </Select>
        )}
      />
    </FormGroup>
  );
};
