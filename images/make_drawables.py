import sys
import os
import yaml
from dpi_manager import DPIManager, ImageTypeException
from settings import *
from utils import (
    ensure_dir,
)


def main(args):
    test = False

    if '--help' in args:
        print("--dry-run: dry run mode, outputs to ./test folder")

    if '--test' in args:
        print("Entering Test Mode")
        print("All results outputted to ./test folder")
        test = True

    dir_target = DIR_TEST if test else DIR_BUILD
    target_path = os.path.join(os.path.dirname(__file__), dir_target)

    if not test:
        # confirm
        confirm = raw_input("""
            Are you sure you want to build image resources now? This will
            overwrite any existing resources with similar names as the source
            images in %s. Type 'yes' to continue.""" % target_path)
        if not confirm == 'yes':
            print "Aborting building resources."
            return

    make_drawables(target_path)


def make_drawables(target_path):
    ensure_dir(target_path)
    directory_spec = os.path.join(os.path.dirname(__file__), DIR_SPEC)
    src_path = os.path.join(os.path.dirname(__file__), DIR_SRC)

    for spec in os.listdir(directory_spec):
        spec_file = os.path.join(directory_spec, spec)
        with open(spec_file) as f:
            spec_src = yaml.load(f.read())
            try:
                dpi_manager = DPIManager(spec_src, src_path, target_path)
                dpi_manager.update_resources()
            except ImageTypeException as e:
                print("Encountered an ImageTypeException for %(spec)s: "
                      "%(error)s" % {
                          'spec': spec,
                          'error': e.message,
                      })


if __name__ == '__main__':
    main(sys.argv[1:])
