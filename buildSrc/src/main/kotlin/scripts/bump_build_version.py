import os
import requests
import sys

var_name = sys.argv[1]

repo = os.environ["GITHUB_REPOSITORY"]
token = os.environ["GH_TOKEN"]
api_url = f"https://api.github.com/repos/{repo}/actions/variables/{var_name}"
headers = {
    "Authorization": f"Bearer {token}",
    "Accept": "application/vnd.github+json",
}


def get_variable():
    response = requests.get(api_url, headers=headers)
    if response.status_code == 404:
        return None
    response.raise_for_status()
    return int(response.json()["value"])


def create_variable():
    response = requests.post(
        f"https://api.github.com/repos/{repo}/actions/variables",
        headers=headers,
        json={"name": var_name, "value": "1"}
    )
    response.raise_for_status()
    return 1


def update_variable(value):
    response = requests.patch(
        api_url,
        headers=headers,
        json={"name": var_name, "value": str(value)}
    )
    response.raise_for_status()
    return value


def increment_variable():
    current_value = get_variable()
    if current_value is None:
        return create_variable()
    else:
        return update_variable(current_value + 1)


new_value = increment_variable()
print(new_value)
