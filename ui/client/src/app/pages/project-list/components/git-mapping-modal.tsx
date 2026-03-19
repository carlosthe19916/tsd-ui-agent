import React from "react";

import {
  Bullseye,
  Button,
  ButtonVariant,
  FormGroup,
  FormSelect,
  FormSelectOption,
  Label,
  LabelGroup,
  Modal,
  ModalBody,
  ModalFooter,
  ModalHeader,
  Spinner,
  TextInput,
} from "@patternfly/react-core";
import { TrashIcon } from "@patternfly/react-icons";
import { Table, Tbody, Td, Th, Thead, Tr } from "@patternfly/react-table";

import type { GitDto, ProjectDto } from "@app/api/models";
import { useFetchGits } from "@app/queries/gits";
import {
  useCreateMappingMutation,
  useDeleteMappingMutation,
  useFetchMappings,
} from "@app/queries/project-git-mappings";

interface GitMappingModalProps {
  project: ProjectDto;
  isOpen: boolean;
  onClose: () => void;
}

export const GitMappingModal: React.FC<GitMappingModalProps> = ({
  project,
  isOpen,
  onClose,
}) => {
  if (!isOpen) return null;
  return <GitMappingModalContent project={project} onClose={onClose} />;
};

const GitMappingModalContent: React.FC<
  Omit<GitMappingModalProps, "isOpen">
> = ({ project, onClose }) => {
  const { data: mappings, isLoading: mappingsLoading } = useFetchMappings(
    project.id,
  );
  const { data: gits, isLoading: gitsLoading } = useFetchGits();

  const deleteMutation = useDeleteMappingMutation(project.id);
  const createMutation = useCreateMappingMutation(project.id);

  const [selectedGitId, setSelectedGitId] = React.useState<string>("");
  const [selectedSpace, setSelectedSpace] = React.useState<string>("");
  const [labelInput, setLabelInput] = React.useState<string>("");
  const [labels, setLabels] = React.useState<string[]>([]);

  const isLoading = mappingsLoading || gitsLoading;

  const gitMap = React.useMemo(() => {
    const map = new Map<number, GitDto>();
    for (const g of gits ?? []) {
      map.set(g.id, g);
    }
    return map;
  }, [gits]);

  const handleAddLabel = () => {
    const trimmed = labelInput.trim();
    if (trimmed && !labels.includes(trimmed)) {
      setLabels([...labels, trimmed]);
    }
    setLabelInput("");
  };

  const handleLabelKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === "Enter") {
      e.preventDefault();
      handleAddLabel();
    }
  };

  const handleRemoveLabel = (label: string) => {
    setLabels(labels.filter((l) => l !== label));
  };

  const handleAdd = () => {
    if (!selectedGitId || !selectedSpace) return;
    createMutation.mutate(
      {
        projectId: project.id,
        gitId: Number(selectedGitId),
        space: selectedSpace,
        labels,
      },
      {
        onSuccess: () => {
          setSelectedGitId("");
          setSelectedSpace("");
          setLabels([]);
          setLabelInput("");
        },
      },
    );
  };

  return (
    <Modal
      variant="large"
      isOpen
      onClose={onClose}
      aria-label="Git repository mappings"
    >
      <ModalHeader title={`Git Repository Mappings \u2014 "${project.name}"`} />
      <ModalBody>
        {isLoading ? (
          <Bullseye>
            <Spinner />
          </Bullseye>
        ) : (
          <>
            <Table aria-label="Existing mappings" variant="compact">
              <Thead>
                <Tr>
                  <Th>Repository</Th>
                  <Th>Space</Th>
                  <Th>Labels</Th>
                  <Th />
                </Tr>
              </Thead>
              <Tbody>
                {(mappings ?? []).length === 0 ? (
                  <Tr>
                    <Td colSpan={4}>
                      <Bullseye>No mappings configured yet.</Bullseye>
                    </Td>
                  </Tr>
                ) : (
                  (mappings ?? []).map((m) => (
                    <Tr key={m.id}>
                      <Td>{gitMap.get(m.gitId)?.url ?? `Git #${m.gitId}`}</Td>
                      <Td>{m.space}</Td>
                      <Td>
                        <LabelGroup>
                          {(m.labels ?? []).map((l) => (
                            <Label key={l}>{l}</Label>
                          ))}
                        </LabelGroup>
                      </Td>
                      <Td isActionCell>
                        <Button
                          variant={ButtonVariant.plain}
                          aria-label="Delete mapping"
                          onClick={() => deleteMutation.mutate(m.id)}
                          isDisabled={deleteMutation.isPending}
                        >
                          <TrashIcon />
                        </Button>
                      </Td>
                    </Tr>
                  ))
                )}
              </Tbody>
            </Table>

            <div style={{ marginTop: "1.5rem" }}>
              <strong>Add mapping</strong>

              <div
                style={{
                  display: "grid",
                  gridTemplateColumns: "1fr 1fr 1fr",
                  gap: "1rem",
                  marginTop: "0.5rem",
                }}
              >
                <FormGroup label="Repository" fieldId="mapping-git">
                  <FormSelect
                    id="mapping-git"
                    value={selectedGitId}
                    onChange={(_e, val) => setSelectedGitId(val)}
                  >
                    <FormSelectOption
                      value=""
                      label="Select a repository..."
                      isPlaceholder
                    />
                    {(gits ?? []).map((g) => (
                      <FormSelectOption
                        key={g.id}
                        value={String(g.id)}
                        label={g.url}
                      />
                    ))}
                  </FormSelect>
                </FormGroup>

                <FormGroup label="Space prefix" fieldId="mapping-space">
                  <TextInput
                    id="mapping-space"
                    value={selectedSpace}
                    onChange={(_e, val) => setSelectedSpace(val)}
                    placeholder="e.g. MYPROJECT"
                  />
                </FormGroup>

                <FormGroup label="Labels" fieldId="mapping-labels">
                  <LabelGroup style={{ marginBottom: "0.25rem" }}>
                    {labels.map((l) => (
                      <Label key={l} onClose={() => handleRemoveLabel(l)}>
                        {l}
                      </Label>
                    ))}
                  </LabelGroup>
                  <TextInput
                    id="mapping-labels"
                    value={labelInput}
                    onChange={(_e, val) => setLabelInput(val)}
                    onKeyDown={handleLabelKeyDown}
                    onBlur={handleAddLabel}
                    placeholder="Type and press Enter..."
                  />
                </FormGroup>
              </div>

              <Button
                variant={ButtonVariant.primary}
                isDisabled={
                  !selectedGitId || !selectedSpace || createMutation.isPending
                }
                onClick={handleAdd}
                style={{ marginTop: "0.5rem" }}
              >
                Add
              </Button>
            </div>
          </>
        )}
      </ModalBody>
      <ModalFooter>
        <Button variant={ButtonVariant.link} onClick={onClose}>
          Close
        </Button>
      </ModalFooter>
    </Modal>
  );
};
