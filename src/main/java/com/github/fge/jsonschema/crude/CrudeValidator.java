/*
 * Copyright (c) 2013, Francis Galiegue <fgaliegue@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the Lesser GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * Lesser GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.github.fge.jsonschema.crude;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jsonschema.exceptions.ProcessingException;
import com.github.fge.jsonschema.library.DraftV3Library;
import com.github.fge.jsonschema.library.DraftV4Library;
import com.github.fge.jsonschema.library.SchemaVersion;
import com.github.fge.jsonschema.load.Dereferencing;
import com.github.fge.jsonschema.load.LoadingConfiguration;
import com.github.fge.jsonschema.load.SchemaLoader;
import com.github.fge.jsonschema.processing.Processor;
import com.github.fge.jsonschema.processing.ProcessorChain;
import com.github.fge.jsonschema.processing.ProcessorSelector;
import com.github.fge.jsonschema.processors.data.FullValidationContext;
import com.github.fge.jsonschema.processors.data.ValidationData;
import com.github.fge.jsonschema.processors.ref.RefResolverProcessor;
import com.github.fge.jsonschema.processors.validation.ValidationChain;
import com.github.fge.jsonschema.processors.validation.ValidationProcessor;
import com.github.fge.jsonschema.report.ListProcessingReport;
import com.github.fge.jsonschema.report.ProcessingReport;
import com.github.fge.jsonschema.tree.JsonTree;
import com.github.fge.jsonschema.tree.SchemaTree;
import com.github.fge.jsonschema.tree.SimpleJsonTree;

import java.io.IOException;

import static com.github.fge.jsonschema.library.SchemaVersion.*;

public final class CrudeValidator
{
    private final SchemaLoader loader;
    private final Processor<ValidationData, ProcessingReport> validator;

    public CrudeValidator(final SchemaVersion version,
        final Dereferencing dereferencing)
        throws IOException
    {
        final LoadingConfiguration cfg = LoadingConfiguration.newConfiguration()
            .removeScheme("file").removeScheme("resource").removeScheme("jar")
            .dereferencing(dereferencing).freeze();

        loader = new SchemaLoader(cfg);

        final RefResolverProcessor refResolver = new RefResolverProcessor(loader);

        final Processor<ValidationData, FullValidationContext> draftv4
            = new ValidationChain(DraftV4Library.get());
        final Processor<ValidationData, FullValidationContext> draftv3
            = new ValidationChain(DraftV3Library.get());

        final Processor<ValidationData, FullValidationContext> byDefault
            = version == DRAFTV4 ? draftv4 : draftv3;

        final Processor<ValidationData, FullValidationContext> processor
            = new ProcessorSelector<ValidationData, FullValidationContext>()
                .when(DRAFTV4.versionTest()).then(draftv4)
                .when(DRAFTV3.versionTest()).then(draftv3)
                .otherwise(byDefault).getProcessor();

        final Processor<ValidationData, FullValidationContext> fullChain
            = ProcessorChain.startWith(refResolver).chainWith(processor)
            .end();
        validator = new ValidationProcessor(fullChain);
    }

    public ProcessingReport validate(final JsonNode schema,
        final JsonNode instance)
        throws ProcessingException
    {
        final ValidationData data = buildData(schema, instance);
        final ListProcessingReport report = new ListProcessingReport();
        return validator.process(report, data);
    }

    public ProcessingReport validateUnchecked(final JsonNode schema,
        final JsonNode instance)
    {
        final ValidationData data = buildData(schema, instance);
        final ListProcessingReport report = new ListProcessingReport();
        try {
            return validator.process(report, data);
        } catch (ProcessingException e) {
            return new UncheckedReport(e, report);
        }
    }

    private ValidationData buildData(final JsonNode schema,
        final JsonNode node)
    {
        final SchemaTree tree = loader.load(schema);
        final JsonTree instance = new SimpleJsonTree(node);
        return new ValidationData(tree, instance);
    }
}
