module.exports = function (grunt) {

    var DEBUG = false;

    var manifest = grunt.file.readJSON('manifest.json');

    grunt.initConfig({
        pkg: grunt.file.readJSON('package.json'),
        uglify: {
            options: {
                banner: '/*! <%= pkg.name %> <%= grunt.template.today("yyyy-mm-dd") %> */\n',
                beautify: DEBUG,
                mangle: !DEBUG
            },

            dist: {
                files: {
                    'build/popup.js': [
                        'bower_components/jquery/dist/jquery.js',
                        'bower_components/spin.js/spin.js',
                        'bower_components/spin.js/jquery.spin.js',
                        'bower_components/underscore/underscore.js',
                        'bower_components/backbone/backbone.js',
                        'src/popup.js'
                    ]
                }
            }
        },

        jshint: {
            all: ['Gruntfile.js', 'src/3*.js']
        },

        exec: {
            pack: 'zip -r ../builds/knapsack-chrome-extension-' + manifest.version + '.zip build'
        }
    });

    grunt.loadNpmTasks('grunt-contrib-uglify');
    grunt.loadNpmTasks('grunt-contrib-jshint');
    grunt.loadNpmTasks('grunt-exec');

    grunt.registerTask('default', function () {
        grunt.task.run(['uglify']);

        grunt.file.copy('assets/icon.png', 'build/icon.png');
        grunt.file.copy('assets/popup.html', 'build/popup.html');
        grunt.file.copy('assets/popup.css', 'build/popup.css');
        grunt.file.copy('manifest.json', 'build/manifest.json');
        grunt.file.copy('key.pem', 'build/key.pem');

        grunt.task.run(['exec:pack']);

    });

};
