package com.customentityhighlight;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Where a group draws its label relative to each highlighted entity's model.
 */
@Getter
@RequiredArgsConstructor
public enum LabelPosition
{
	ABOVE("Above"),
	MIDDLE("Middle"),
	BELOW("Below");

	private final String name;

	@Override
	public String toString()
	{
		return name;
	}
}
