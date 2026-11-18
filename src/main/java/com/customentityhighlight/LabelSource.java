package com.customentityhighlight;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * What text a group draws as each highlighted entity's label when it shows names.
 */
@Getter
@RequiredArgsConstructor
public enum LabelSource
{
	NAME("Name"),
	MATCH("Match"),
	CUSTOM("Custom");

	private final String name;

	@Override
	public String toString()
	{
		return name;
	}
}
