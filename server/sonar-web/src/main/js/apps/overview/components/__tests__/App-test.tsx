/*
 * SonarQube
 * Copyright (C) 2009-2022 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
import { shallow } from 'enzyme';
import * as React from 'react';
import { isSonarCloud } from '../../../../helpers/system';
import { mockAppState } from '../../../../helpers/testMocks';
import BranchOverview from '../../branches/BranchOverview';
import { App } from '../App';

jest.mock('../../../../helpers/system', () => ({ isSonarCloud: jest.fn() }));

const component = {
  key: 'foo',
  analysisDate: '2016-01-01',
  breadcrumbs: [],
  name: 'Foo',
  qualifier: 'TRK',
  version: '0.0.1'
};

beforeEach(() => {
  (isSonarCloud as jest.Mock<any>).mockClear();
  (isSonarCloud as jest.Mock<any>).mockReturnValue(false);
});

it('should render BranchOverview', () => {
  expect(
    getWrapper()
      .find(BranchOverview)
      .exists()
  ).toBe(true);
});

function getWrapper(props = {}) {
  return shallow(
    <App appState={mockAppState()} branchLikes={[]} component={component} {...props} />
  );
}
