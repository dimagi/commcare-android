import os
from PIL import Image
import numpy
from utils import ensure_dir

DRAWABLE = 'drawable'


class ImageTypeException(Exception):
    pass


class Density(object):
    LDPI = 'ldpi'
    MDPI = 'mdpi'
    HDPI = 'hdpi'
    XHDPI = 'xhdpi'
    XXHDPI = 'xxhdpi'
    XXXHDPI = 'xxxhdpi'

    RATIOS = {
        LDPI: 3,
        MDPI: 4,
        HDPI: 6,
        XHDPI: 8,
        XXHDPI: 12,
        XXXHDPI: 16,
    }

    ORDER = [LDPI, MDPI, HDPI, XHDPI, XXHDPI, XXXHDPI]


class ImageType(object):
    PNG = 'png'
    PNG_9_BIT = '9.png'
    SVG = 'svg'

    # haven't flushed out SVG support yet, or scaling 9-bits
    SUPPORTED_TYPES = [PNG]

    @classmethod
    def is_supported_type(cls, image_type):
        return image_type in cls.SUPPORTED_TYPES


class ImageSpec(object):

    def __init__(self, src):
        self.filename = src['filename']
        self.source_dpi = src['source_dpi']
        self.other_scaling = src.get('other_scaling', {})
        self.excluded_dpis = src.get('excluded_dpis', [])

        # Determine Image Type by filename
        extension = self.filename.split('.', 1)[1]
        if not ImageType.is_supported_type(extension):
            raise ImageTypeException(
                'The image type %(ext)s is not yet supported.' % {
                    'ext': extension,
                })


class DPIManager(object):

    def __init__(self, spec_src, source_folder, target_folder):
        """The DPIManager handles all the scaling of an image according to its
        spec and ImageType.
        :param spec_src:
        :param source_folder:
        :return:
        """
        self.source_folder = source_folder
        self.target_folder = target_folder
        self.spec = ImageSpec(spec_src)

        src_dpi_index = Density.ORDER.index(self.spec.source_dpi) + 1
        target_dpis = set(Density.ORDER[:src_dpi_index])

        self.target_dpis = list(target_dpis.difference(self.spec.excluded_dpis))
        self.scaling_ratios = self.get_scaling_ratios()

    def get_scaling_ratios(self):
        src_scale = Density.RATIOS[self.spec.source_dpi]
        scaling = {}
        for dpi in self.target_dpis:
            scaling[dpi] = Density.RATIOS[dpi] / float(src_scale)
        return scaling

    def update_resources(self):
        src_path = os.path.join(self.source_folder, self.spec.filename)
        src_img = Image.open(src_path)

        # Premult alpha resizing, to avoid halo effect
        # http://stackoverflow.com/questions/9142825/transparent-png-resizing-with-python-image-library-and-the-halo-effect
        premult = numpy.fromstring(src_img.tobytes(), dtype=numpy.uint8)
        alphaLayer = premult[3::4] / 255.0
        premult[::4] *= alphaLayer
        premult[1::4] *= alphaLayer
        premult[2::4] *= alphaLayer

        src_img = Image.frombytes("RGBA", src_img.size, premult.tobytes())

        # save original image to drawables
        default_dir = os.path.join(self.target_folder, DRAWABLE)
        ensure_dir(default_dir)
        default_path = os.path.join(default_dir, self.spec.filename)
        src_img.save(default_path)
        print "save to", default_path

        src_width, src_height = src_img.size

        for dpi in self.target_dpis:
            ratio = self.scaling_ratios[dpi]
            dpi_width = int(round(src_width * ratio, 0))
            dpi_height = int(round(src_height * ratio, 0))
            print "scale image %(from_dims)s --> %(to_dims)s" % {
                'from_dims': "%d x %d" % (src_width, src_height),
                'to_dims': "%d x %d" % (dpi_width, dpi_height),
            }
            dpi_dir = os.path.join(
                self.target_folder, '%s-%s' % (DRAWABLE, dpi)
            )
            ensure_dir(dpi_dir)
            dpi_path = os.path.join(dpi_dir, self.spec.filename)
            src_img.resize((dpi_width, dpi_height), Image.ANTIALIAS).save(
                dpi_path
            )
            print "save to", dpi_path

        for label, size in self.spec.other_scaling.items():
            scale_dir = os.path.join(
                self.target_folder, '%s-%s' % (DRAWABLE, label)
            )
            ensure_dir(scale_dir)
            scale_path = os.path.join(scale_dir, self.spec.filename)
            src_img.resize((size[0], size[1]), Image.ANTIALIAS).save(
                scale_path
            )
            print "save to", scale_path
