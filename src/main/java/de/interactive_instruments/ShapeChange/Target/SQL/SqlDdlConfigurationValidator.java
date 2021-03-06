/**
 * ShapeChange - processing application schemas for geographic information
 *
 * This file is part of ShapeChange. ShapeChange takes a ISO 19109
 * Application Schema from a UML model and translates it into a
 * GML Application Schema or other implementation representations.
 *
 * Additional information about the software can be found at
 * http://shapechange.net/
 *
 * (c) 2002-2016 interactive instruments GmbH, Bonn, Germany
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Contact:
 * interactive instruments GmbH
 * Trierer Strasse 70-72
 * 53115 Bonn
 * Germany
 */
package de.interactive_instruments.ShapeChange.Target.SQL;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;

import org.apache.commons.lang3.StringUtils;

import de.interactive_instruments.ShapeChange.ConfigurationValidator;
import de.interactive_instruments.ShapeChange.MapEntryParamInfos;
import de.interactive_instruments.ShapeChange.MessageSource;
import de.interactive_instruments.ShapeChange.Options;
import de.interactive_instruments.ShapeChange.ProcessConfiguration;
import de.interactive_instruments.ShapeChange.ProcessMapEntry;
import de.interactive_instruments.ShapeChange.ShapeChangeResult;
import de.interactive_instruments.ShapeChange.ShapeChangeResult.MessageContext;

/**
 * @author Johannes Echterhoff (echterhoff at interactive-instruments dot
 *         de)
 */
public class SqlDdlConfigurationValidator
		implements ConfigurationValidator, MessageSource {

	// these fields will be initialized when isValid(...) is called
	private ProcessConfiguration config = null;
	private Options options = null;
	private ShapeChangeResult result = null;

	@Override
	public boolean isValid(ProcessConfiguration config, Options options,
			ShapeChangeResult result) {

		this.config = config;
		this.options = options;
		this.result = result;

		boolean isValid = true;

		SortedMap<String, ProcessMapEntry> mapEntryByType = new TreeMap<String, ProcessMapEntry>();

		for (ProcessMapEntry pme : config.getMapEntries()) {
			mapEntryByType.put(pme.getType(), pme);
		}

		// general validation of map entry parameters
		MapEntryParamInfos mepis = new MapEntryParamInfos(result,
				mapEntryByType.values());

		isValid = isValid && mepis.isValid();

		DatabaseStrategy databaseStrategy;
		String databaseSystem = config
				.getParameterValue(SqlConstants.PARAM_DATABASE_SYSTEM);
		if (databaseSystem == null
				|| "postgresql".equalsIgnoreCase(databaseSystem)) {
			databaseStrategy = new PostgreSQLStrategy();
		} else if ("oracle".equalsIgnoreCase(databaseSystem)) {
			databaseStrategy = new OracleStrategy(result);
		} else if ("sqlserver".equalsIgnoreCase(databaseSystem)) {
			databaseStrategy = new SQLServerStrategy(result);
		} else if ("sqlite".equalsIgnoreCase(databaseSystem)) {
			databaseStrategy = new SQLServerStrategy(result);
		} else {
			databaseStrategy = new PostgreSQLStrategy();
			result.addError(this, 100, SqlConstants.PARAM_DATABASE_SYSTEM,
					databaseSystem);
			isValid = false;
		}

		// validation of common sql map entry parameters
		isValid = isValid && checkCommonMapEntryParameters(mepis);

		// validation of database strategy specific sql map entry parameters
		isValid = isValid && databaseStrategy.validate(mapEntryByType, mepis);

		// validation of length precision scale pattern
		isValid = isValid && checkLengthPrecisionScalePattern(
				config.getMapEntries(), mepis);

		isValid = isValid
				&& checkDescriptorsForCodeList(config, options, result);

		isValid = isValid && checkIntegerParameter(SqlConstants.PARAM_SIZE);
		isValid = isValid
				&& checkIntegerParameter(SqlConstants.PARAM_CODE_NAME_SIZE);

		// --------------------------
		String fileDdlTop = config.parameterAsString(
				SqlConstants.PARAM_FILE_DDL_TOP, null, false, true);
		if (fileDdlTop != null) {
			File ddlTop = new File(fileDdlTop);
			if (!ddlTop.exists() || ddlTop.isDirectory() || !ddlTop.canRead()) {
				isValid = false;
				result.addError(this, 5, SqlConstants.PARAM_FILE_DDL_TOP,
						fileDdlTop);
			}
		}

		String fileDdlBottom = config.parameterAsString(
				SqlConstants.PARAM_FILE_DDL_BOTTOM, null, false, true);
		if (fileDdlBottom != null) {
			File ddlBottom = new File(fileDdlBottom);
			if (!ddlBottom.exists() || ddlBottom.isDirectory()
					|| !ddlBottom.canRead()) {
				isValid = false;
				result.addError(this, 5, SqlConstants.PARAM_FILE_DDL_BOTTOM,
						fileDdlBottom);
			}
		}

		String lengthQualifier = config.parameterAsString(
				SqlConstants.PARAM_LENGTH_QUALIFIER, null, false, true);

		if (lengthQualifier != null) {

			if (!("BYTE".equalsIgnoreCase(lengthQualifier)
					|| "CHAR".equalsIgnoreCase(lengthQualifier))) {

				isValid = false;
				result.addError(this, 100, SqlConstants.PARAM_LENGTH_QUALIFIER,
						lengthQualifier);
			}
		}

		return isValid;
	}

	private boolean checkLengthPrecisionScalePattern(
			List<ProcessMapEntry> mapEntries,
			MapEntryParamInfos mapEntryParamInfos) {

		boolean isValid = true;

		for (ProcessMapEntry me : mapEntries) {

			String typeRuleKey = me.getType() + "#" + me.getRule();

			if (mapEntryParamInfos.hasParameter(me,
					SqlConstants.ME_PARAM_LENGTH)
					&& mapEntryParamInfos.hasParameter(me,
							SqlConstants.ME_PARAM_PRECISION)) {

				/*
				 * length and precision must not occur as parameters in the same
				 * map entry
				 */

				isValid = false;
				result.addError(this, 105, typeRuleKey,
						SqlConstants.ME_PARAM_LENGTH,
						SqlConstants.ME_PARAM_PRECISION);

			} else if (mapEntryParamInfos.hasParameter(me,
					SqlConstants.ME_PARAM_LENGTH)
					|| mapEntryParamInfos.hasParameter(me,
							SqlConstants.ME_PARAM_PRECISION)) {

				/*
				 * Only if length or precision are present as parameters do we
				 * parse and validate the target type. This supports direct
				 * parameterization for other kinds of target types.
				 */

				Matcher lengthPrecisionScale = SqlConstants.PATTERN_ME_TARGETTYPE_LENGTH_PRECISION_SCALE
						.matcher(me.getTargetType().trim());

				if (lengthPrecisionScale.matches()) {

					String group2 = lengthPrecisionScale.group(2);
					String group3 = lengthPrecisionScale.group(3);

					if (mapEntryParamInfos.hasParameter(me,
							SqlConstants.ME_PARAM_LENGTH)) {

						// we only expect a single non-negative integer
						isValid = isValid
								&& checkNonNegativeInteger(group2, typeRuleKey);

						if (group3 != null) {
							isValid = false;
							result.addError(this, 104, typeRuleKey,
									SqlConstants.ME_PARAM_LENGTH);
						}

					} else if (mapEntryParamInfos.hasParameter(me,
							SqlConstants.ME_PARAM_PRECISION)) {

						/*
						 * we expect a non-negative integer for the first number
						 * (group2)
						 */
						isValid = isValid
								&& checkNonNegativeInteger(group2, typeRuleKey);

						if (group3 != null) {

							/*
							 * we expect a non-negative integer for the second
							 * number (group3)
							 */
							isValid = isValid && checkNonNegativeInteger(group3,
									typeRuleKey);
						}
					}
				}
			}
		}

		return isValid;
	}

	private boolean checkNonNegativeInteger(String number, String typeRuleKey) {
		try {
			Integer i = Integer.parseInt(number);
			if (i < 0) {
				result.addError(this, 103, typeRuleKey);
				return false;
			}
		} catch (NumberFormatException e) {
			result.addError(this, 103, typeRuleKey);
			return false;
		}
		return true;
	}

	private boolean checkCommonMapEntryParameters(MapEntryParamInfos mepp) {

		boolean isValid = true;

		for (Entry<String, Map<String, Map<String, String>>> entry : mepp
				.getParameterCache().entrySet()) {

			String typeRuleKey = entry.getKey();
			Map<String, Map<String, String>> characteristicsByParameter = entry
					.getValue();

			if (characteristicsByParameter
					.containsKey(SqlConstants.ME_PARAM_TABLE)) {

				Map<String, String> tableCharacteristics = characteristicsByParameter
						.get(SqlConstants.ME_PARAM_TABLE);

				if (tableCharacteristics.containsKey(
						SqlConstants.ME_PARAM_TABLE_CHARACT_REP_CAT)) {

					String representedCategory = tableCharacteristics
							.get(SqlConstants.ME_PARAM_TABLE_CHARACT_REP_CAT);

					if (representedCategory == null) {

						isValid = false;
						result.addError(this, 101, typeRuleKey,
								SqlConstants.ME_PARAM_TABLE_CHARACT_REP_CAT,
								SqlConstants.ME_PARAM_TABLE);

					} else if (!representedCategory.matches(
							SqlConstants.ME_PARAM_TABLE_CHARACT_REP_CAT_VALIDATION_REGEX)) {

						isValid = false;
						result.addError(this, 102, typeRuleKey,
								SqlConstants.ME_PARAM_TABLE_CHARACT_REP_CAT,
								SqlConstants.ME_PARAM_TABLE,
								SqlConstants.ME_PARAM_TABLE_CHARACT_REP_CAT_VALIDATION_REGEX);
					}
				}
			}

			if (characteristicsByParameter
					.containsKey(SqlConstants.ME_PARAM_LENGTH)) {

				Map<String, String> lengthCharacteristics = characteristicsByParameter
						.get(SqlConstants.ME_PARAM_LENGTH);

				if (lengthCharacteristics.containsKey(
						SqlConstants.ME_PARAM_LENGTH_CHARACT_LENGTH_QUALIFIER)) {

					String lengthQualifier = lengthCharacteristics
							.get(SqlConstants.ME_PARAM_LENGTH_CHARACT_LENGTH_QUALIFIER);

					if (!("BYTE".equalsIgnoreCase(lengthQualifier)
							|| "CHAR".equalsIgnoreCase(lengthQualifier)
							|| "NONE".equalsIgnoreCase(lengthQualifier))) {

						isValid = false;
						result.addError(this, 106, typeRuleKey,
								lengthQualifier == null ? "<null>" : lengthQualifier,
								SqlConstants.ME_PARAM_LENGTH_CHARACT_LENGTH_QUALIFIER,
								SqlConstants.ME_PARAM_LENGTH);
					}
				}
			}
		}

		return isValid;
	}

	private boolean checkIntegerParameter(String paramName) {

		boolean isValid = true;

		String valueByConfig = config.getParameterValue(paramName);

		if (valueByConfig != null) {

			try {
				Integer.parseInt(valueByConfig);
			} catch (NumberFormatException e) {
				MessageContext mc = result.addWarning(this, 4, paramName,
						e.getMessage());
				mc.addDetail(this, 0);
				isValid = false;
			}
		}

		return isValid;
	}

	private boolean checkDescriptorsForCodeList(ProcessConfiguration config,
			Options options, ShapeChangeResult result) {

		boolean isValid = true;

		String descriptorsForCodelistByConfig = config
				.getParameterValue(SqlConstants.PARAM_DESCRIPTORS_FOR_CODELIST);

		if (StringUtils.isNotBlank(descriptorsForCodelistByConfig)) {

			String fullRegex = SqlConstants.DESCRIPTORS_FOR_CODELIST_REGEX
					+ "(," + SqlConstants.DESCRIPTORS_FOR_CODELIST_REGEX + ")*";

			if (descriptorsForCodelistByConfig.matches(fullRegex)) {

				SortedSet<String> descriptorsForCodelist = new TreeSet<String>();

				String[] descriptorsForCodelistFromConfig = descriptorsForCodelistByConfig
						.trim().split("(?<!\\\\),");

				boolean unknownDescriptorFound = false;
				for (String tmp : descriptorsForCodelistFromConfig) {

					if (tmp.matches(
							SqlConstants.DESCRIPTORS_FOR_CODELIST_REGEX)) {
						descriptorsForCodelist.add(tmp);
					} else {
						unknownDescriptorFound = true;
					}
				}
				if (unknownDescriptorFound) {
					result.addError(this, 2, descriptorsForCodelistByConfig,
							SqlConstants.DESCRIPTORS_FOR_CODELIST_REGEX);
					isValid = false;
				}
				if (descriptorsForCodelist.isEmpty()) {
					result.addError(this, 3);
					isValid = false;
					// irrelevant here:
					// descriptorsForCodelist.add("documentation");
				}
			} else {

				result.addError(this, 6, fullRegex);
				isValid = false;
			}
		}

		return isValid;
	}

	@Override
	public String message(int mnr) {

		switch (mnr) {
		case 0:
			return "Context: SqlDdl target configuration element with 'inputs'='$1$'.";
		case 1:
			return "For further details, see the documentation of parameter '$1$' on http://shapechange.net/targets/sql-ddl/";
		case 2:
			return "At least one of the descriptor identifiers in configuration parameter '"
					+ SqlConstants.PARAM_DESCRIPTORS_FOR_CODELIST
					+ "' - the parameter value is '$1$' - does not match the regular expression '$2$'. Correct the parameter value.";
		case 3:
			return "Configuration parameter '"
					+ SqlConstants.PARAM_DESCRIPTORS_FOR_CODELIST
					+ "' did not contain a well-known identifier. Use well-known identifiers or omit the parameter.";
		case 4:
			return "Number format exception while converting the value of configuration parameter '$1$' to an integer. Exception message: $2$.";
		case 5:
			return "Value of configuration parameter '$1$' is '$2$'. The file does not exist, is a directory, or cannot be read.";
		case 6:
			return "Configuration parameter '"
					+ SqlConstants.PARAM_DESCRIPTORS_FOR_CODELIST
					+ "' does not match the following regular expression: $1$";

		case 100:
			return "Parameter '$1$' is set to '$2$'. This is not a valid value.";
		case 101:
			return "Invalid map entry for type#rule '$1$': no value is provided for the characteristic '$2$' of parameter '$3$'.";
		case 102:
			return "Invalid map entry for type#rule '$1$': value provided for characteristic '$2$' of parameter '$3$' is invalid. Check that the value matches the regular expression: $4$.";
		case 103:
			return "Invalid map entry for type#rule '$1$': the targetType contains a parameterization that is not a non-negative integer.";
		case 104:
			return "Invalid map entry for type#rule '$1$': the map entry has paramter '$2$' but the targetType has a parameterization with two numbers. With parameter '$2$', the targetType of the map entry may only have a single number.";
		case 105:
			return "Invalid map entry for type#rule '$1$': the map entry has both parameter '$2$' and '$3$'. Only one of these parameters is allowed per map entry.";
		case 106:
			return "Invalid map entry for type#rule '$1$': value '$2$' provided for characteristic '$3$' of parameter '$4$' is invalid.";

		default:
			return "(" + SqlDdlConfigurationValidator.class.getName()
					+ ") Unknown message with number: " + mnr;
		}
	}
}
