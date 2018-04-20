import logging
import os

import sdk_cmd


TIMEOUT_SECONDS = 15 * 60
SHORT_TIMEOUT_SECONDS = 30

log = logging.getLogger(__name__)

def copy_job(service_name, src_name, dst_name, timeout_seconds=SHORT_TIMEOUT_SECONDS):
    path = 'createItem?name={}&mode=copy&from={}'.format(dst_name, src_name)
    sdk_cmd.service_request('POST', service_name, path, timeout_seconds=timeout_seconds)

    # Copy starts jobs off disable and you have to disable them and enable again to get them "buildable"
    # https://github.com/entagen/jenkins-build-per-branch/issues/41
    disable_job(service_name, dst_name)
    enable_job(service_name, dst_name)


def enable_job(service_name, job_name, timeout_seconds=SHORT_TIMEOUT_SECONDS):
    return _set_buildable(service_name, job_name, True, timeout_seconds)


def disable_job(service_name, job_name, timeout_seconds=SHORT_TIMEOUT_SECONDS):
    return _set_buildable(service_name, job_name, False, timeout_seconds)


def _set_buildable(service_name, job_name, buildable, timeout_seconds=SHORT_TIMEOUT_SECONDS):
    verb = None

    if buildable:
        verb = 'enable'
    else:
        verb = 'disable'

    path = 'job/{}/{}'.format(job_name, verb)
    return sdk_cmd.service_request('POST', service_name, path, timeout_seconds=timeout_seconds)


def get_jobs(service_name, timeout_seconds=SHORT_TIMEOUT_SECONDS):
    return _get_jenkins_root(service_name, timeout_seconds)['jobs']


def get_job(service_name, job_name, timeout_seconds=SHORT_TIMEOUT_SECONDS):
    path = _get_jenkins_json_path(service_name, 'job/{}'.format(job_name))
    return _get_jenkins_json(service_name, path, timeout_seconds)


def get_builds(service_name, job_name, timeout_seconds=SHORT_TIMEOUT_SECONDS):
    return get_job(service_name, job_name)['builds']


def get_build(service_name, job_name, number, timeout_seconds=SHORT_TIMEOUT_SECONDS):
    path = _get_jenkins_json_path(service_name, 'job/{}/{}'.format(job_name, number))
    return _get_jenkins_json(service_name, path, timeout_seconds)


def get_first_build(service_name, job_name, timeout_seconds=SHORT_TIMEOUT_SECONDS):
    return _get_named_build(service_name, job_name, 'firstBuild')


def get_last_build(service_name, job_name, timeout_seconds=SHORT_TIMEOUT_SECONDS):
    return _get_named_build(service_name, job_name, 'lastBuild')


def _get_named_build(service_name, job_name, build_name, timeout_seconds=SHORT_TIMEOUT_SECONDS):
    last_build = get_job(service_name, job_name)[build_name]
    if last_build is None:
        return None;

    return get_build(service_name, job_name, last_build['number'])


def _get_jenkins_root(service_name, timeout_seconds=SHORT_TIMEOUT_SECONDS):
    return _get_jenkins_json(service_name, 'api/json', timeout_seconds)


def _get_jenkins_json(service_name, path, timeout_seconds=SHORT_TIMEOUT_SECONDS):
    return sdk_cmd.service_request('GET', service_name, path, timeout_seconds=timeout_seconds).json()


def _get_jenkins_json_path(service_name, path):
    return '{}/api/json'.format(path)

