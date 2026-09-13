package com.kopite.devspace.journal.presentation.dto;
import com.kopite.devspace.journal.application.command.UpdateJournalCommand;
import jakarta.validation.constraints.*;
import io.swagger.v3.oas.annotations.media.Schema;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.*;
import tools.jackson.databind.annotation.JsonDeserialize;
@JsonDeserialize(using=UpdateJournalRequest.Deserializer.class)
@Schema(description="Explicit nulls, duplicate and undeclared fields are invalid. Text limits use UTF-16 units. Body is preserved. entryDate is independent of audit timestamps.", additionalProperties=Schema.AdditionalPropertiesValue.FALSE)
public record UpdateJournalRequest(@NotNull @Min(1) @Max(9007199254740991L) Long revision,
    @JournalText(max=120,required=false,trim=true) @Schema(minLength=1,maxLength=120) String title,
     @Schema(type="string",format="uuid") String projectId,
    @JournalText(max=20000,required=false) @Schema(minLength=1,maxLength=20000) String body,
     @Pattern(regexp="[0-9]{4}-[0-9]{2}-[0-9]{2}") @Schema(type="string",format="date",description="Calendar date, years 0001 through 9999; no time or timezone") String entryDate) {
    public UpdateJournalCommand command() {
        return new UpdateJournalCommand(revision,title,JournalRequestFields.uuid(projectId,"projectId"),body,entryDate);
    }
    public static class Deserializer extends ValueDeserializer<UpdateJournalRequest> {
        @Override public UpdateJournalRequest deserialize(JsonParser parser,DeserializationContext context) {
            var f=JournalRequestFields.read(parser,true);
            return new UpdateJournalRequest((Long)f.get("revision"),JournalRequestFields.text(f,"title"),JournalRequestFields.text(f,"projectId"),JournalRequestFields.text(f,"body"),JournalRequestFields.text(f,"entryDate"));
        }
    }
}
