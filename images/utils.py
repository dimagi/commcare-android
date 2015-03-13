import os


def ensure_dir(directory):
    """Ensures that the path specified is an existing directory.
    If not, create it"""
    if not os.path.exists(directory):
        os.makedirs(directory)
