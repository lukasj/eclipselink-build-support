#!/bin/bash -e
#
# Copyright (c) 2022 Oracle and/or its affiliates. All rights reserved.
#
# This program and the accompanying materials are made available under the
# terms of the Eclipse Public License v. 2.0 which is available at
# http://www.eclipse.org/legal/epl-2.0,
# or the Eclipse Distribution License v. 1.0 which is available at
# http://www.eclipse.org/org/documents/edl-v10.php.
#
# SPDX-License-Identifier: EPL-2.0 OR BSD-3-Clause
#

#
# Arguments:
# $1 -  EL_BUILD_SUPPORT_VERSION                 - Version to release
# $2 -  NEXT_EL_BUILD_SUPPORT_VERSION            - Next snapshot version to set (e.g. 3.0.1-SNAPSHOT).
# $3 -  DRY_RUN                     - Do not publish artifacts to OSSRH and code changes to GitHub.
# $4 -  OVERWRITE_GIT               - Allows to overwrite existing version in git
# $5 -  OVERWRITE_STAGING           - Allows to overwrite existing version in OSSRH (Jakarta) staging repositories


EL_BUILD_SUPPORT_VERSION="${1}"
NEXT_EL_BUILD_SUPPORT_VERSION="${2}"
DRY_RUN="${3}"
OVERWRITE_GIT="${4}"
OVERWRITE_STAGING="${5}"


export MAVEN_SKIP_RC="true"

. etc/jenkins/includes/maven.incl.sh
. etc/jenkins/includes/nexus.incl.sh

read_version 'EL_BUILD_SUPPORT' "${EL_BUILD_SUPPORT_DIR}"

if [ -z "${EL_BUILD_SUPPORT_RELEASE_VERSION}" ]; then
  echo '-[ Missing required EclipseLink Build Support release version number! ]--------------------------------'
  exit 1
fi

RELEASE_TAG="${EL_BUILD_SUPPORT_RELEASE_VERSION}"
RELEASE_BRANCH="${EL_BUILD_SUPPORT_RELEASE_VERSION}-RELEASE"

if [ ${DRY_RUN} = 'true' ]; then
  echo '-[ Dry run turned on ]----------------------------------------------------------'
  MVN_DEPLOY_ARGS='install'
  echo '-[ Skipping GitHub branch and tag checks ]--------------------------------------'
else
  MVN_DEPLOY_ARGS='deploy'
  GIT_ORIGIN=`git remote`
  echo '-[ Prepare branch ]-------------------------------------------------------------'
  if [[ -n `git branch -r | grep "${GIT_ORIGIN}/${RELEASE_BRANCH}"` ]]; then
    if [ "${OVERWRITE_GIT}" = 'true' ]; then
      echo "${GIT_ORIGIN}/${RELEASE_BRANCH} branch already exists, deleting"
      git push --delete origin "${RELEASE_BRANCH}" && true
    else
      echo "Error: ${GIT_ORIGIN}/${RELEASE_BRANCH} branch already exists"
      exit 1
    fi
  fi
  echo '-[ Release tag cleanup ]--------------------------------------------------------'
  if [[ -n `git ls-remote --tags ${GIT_ORIGIN} | grep "${RELEASE_TAG}"` ]]; then
    if [ "${OVERWRITE_GIT}" = 'true' ]; then
      echo "${RELEASE_TAG} tag already exists, deleting"
      git push --delete origin "${RELEASE_TAG}" && true
    else
      echo "Error: ${RELEASE_TAG} tag already exists"
      exit 1
    fi
  fi
fi

# Always delete local branch if exists
git branch --delete "${RELEASE_BRANCH}" && true
git checkout -b ${RELEASE_BRANCH}

# Always delete local tag if exists
git tag --delete "${RELEASE_TAG}" && true

# Read Maven identifiers
read_mvn_id 'EL_BUILD_SUPPORT' "${EL_BUILD_SUPPORT_DIR}"

# Set Nexus identifiers
EL_BUILD_SUPPORT_STAGING_DESC="${EL_BUILD_SUPPORT_GROUP_ID}:${EL_BUILD_SUPPORT_ARTIFACT_ID}:${EL_BUILD_SUPPORT_RELEASE_VERSION}"
EL_BUILD_SUPPORT_STAGING_KEY=$(echo ${EL_BUILD_SUPPORT_STAGING_DESC} | sed -e 's/\./\\\./g')

# Set release versions
echo '-[ EclipseLink Build Support release version ]--------------------------------------------------------'
set_version 'EL_BUILD_SUPPORT' "${EL_BUILD_SUPPORT_DIR}" "${EL_BUILD_SUPPORT_RELEASE_VERSION}" "${EL_BUILD_SUPPORT_GROUP_ID}" "${EL_BUILD_SUPPORT_ARTIFACT_ID}" ''

if [ "${OVERWRITE_STAGING}" = 'true' ]; then
  drop_artifacts "${ECLIPSELINK_STAGING_KEY}" "${ECLIPSELINK_DIR}"
fi

echo '-[ Deploy artifacts to staging repository ]-----------------------------'
# Verify, sign and deploy release
(cd ${EL_BUILD_SUPPORT_DIR} && \
  mvn --no-transfer-progress -U -C -B -V \
      -Poss-release,staging -DskipTests \
      -DstagingDescription="${EL_BUILD_SUPPORT_STAGING_DESC}" \
      clean ${MVN_DEPLOY_ARGS})

echo '-[ Tag release ]----------------------------------------------------------------'
git tag "${RELEASE_TAG}" -m "EclipseLink Build Support ${EL_BUILD_SUPPORT_RELEASE_VERSION} release"

# Set next release cycle snapshot version
echo '-[ EclipseLink Build Support next snapshot version ]--------------------------------------------------'
set_version 'EL_BUILD_SUPPORT' "${EL_BUILD_SUPPORT_DIR}" "${EL_BUILD_SUPPORT_NEXT_SNAPSHOT}" "${EL_BUILD_SUPPORT_GROUP_ID}" "${EL_BUILD_SUPPORT_ARTIFACT_ID}" ''

if [ ${DRY_RUN} = 'true' ]; then
  echo '-[ Skipping GitHub update ]-----------------------------------------------------'
else
  echo '-[ Push branch and tag to GitHub ]----------------------------------------------'
  git push origin "${RELEASE_BRANCH}"
  git push origin "${RELEASE_TAG}"
fi
