import { TEXT } from "../constants/texts";

export function buildInvalidFieldsMessage(
  errors: unknown,
  labels: Record<string, string>
): string {
  const fields = Object.keys((errors as Record<string, unknown>) || {});

  if (fields.length === 0) {
    return TEXT.formHasErrors;
  }

  const readableFields = fields.map((field) => labels[field] || field);
  return `${TEXT.formHasErrors}: ${readableFields.join(", ")}`;
}
